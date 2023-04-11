package org.ogema.tools.shell.commands;

import java.lang.reflect.Array;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.felix.gogo.jline.Shell;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Converter;
import org.jline.utils.InfoCmp;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author jlapp
 */
@Component(property = {
	Converter.CONVERTER_CLASSES + "=org.ogema.core.model.Resource"
})
public final class ResourceToString implements Converter {

	public static final String ANSI_RESET = "\033[0m";
	public static final String ANSI_DEC_BOLD = "\033[1m";
	public static final String ANSI_DEC_DIM = "\033[2m";
	public static final String ANSI_DEC_ITALIC = "\033[3m";
	public static final String ANSI_DEC_UNDERLINE = "\033[4m";
	public static final String ANSI_DEC_BLINK = "\033[5m";
	public static final String ANSI_DEC_REVERSE = "\033[7m";
	
	public static final String ANSI_BLACK = "\033[0;30m";
	public static final String ANSI_RED = "\033[0;31m";
	public static final String ANSI_GREEN = "\033[0;32m";
	public static final String ANSI_YELLOW = "\033[0;33m";
	public static final String ANSI_BLUE = "\033[0;34m";
	public static final String ANSI_PURPLE = "\033[0;35m";
	public static final String ANSI_CYAN = "\033[0;36m";
	public static final String ANSI_WHITE = "\033[0;37m";
	//variable background: \033[48;5;<0-255>m
	//variable foreground: \033[38;5;<0-255>m
	public static final String ANSI_BOLD_BLUE = "\033[1;34m";
	
	/**
	 * System property to disable ANSI color codes in output Strings.
	 */
	public static final String NO_COLOR_TERMINAL = "org.ogema.tools.shell.nocolor";

	static String RESET = ANSI_RESET;
	static String TIMESTAMP = ANSI_GREEN;
	static String VALUE = ANSI_YELLOW;
	static String RESOURCE = "\033[38;5;27m";//ANSI_BLUE;
	static String LOCATION = "\033[38;5;27m"+ANSI_DEC_ITALIC;//ANSI_BLUE;
	static String PROP = ANSI_CYAN;
	
	private static boolean NO_COLOR;
	
	private static final Map<String,String> ANSI_MARKUP = new HashMap<String,String>() {
		{
			put("RESET", RESET);
			put("TIMESTAMP", TIMESTAMP);
			put("VALUE", VALUE);
			put("RESOURCE", RESOURCE);
			put("LOCATION", LOCATION);
			put("PROP", PROP);
		}
	};
	
	@Activate
	protected void activate(ComponentContext ctx) {
		NO_COLOR = Boolean.parseBoolean(ctx.getBundleContext().getProperty(NO_COLOR_TERMINAL));
	}
	
	private static Map<String, String> getMarkup() {
		if (NO_COLOR || !outputIsTty() || !terminalSupportsColor()) {
			return Collections.emptyMap();
		}
		return ANSI_MARKUP;
	}
	
	private static boolean outputIsTty() {
		try {
			org.apache.felix.service.command.Process p =
					org.apache.felix.service.command.Process.Utils.current();
			if (p == null) {
				// for command return values that are echoed by the shell, the
				// current process is null. color might be nice here, but there
				// is no way to get the session / terminal at this point.
				return false;
			}
			// false for output redirection to file or pipe
			boolean isTty = p.isTty(1);
			return isTty;
		} catch (RuntimeException re) {
			return false;
		}
	}
	
	private static boolean terminalSupportsColor() {
		try {
			org.apache.felix.service.command.Process p =
					org.apache.felix.service.command.Process.Utils.current();
			if (p == null) {
				return false;
			}
			CommandSession sess = //shellSession.get();
					p.job().session();
			if (sess == null) {
				return false;
			}
			Number mc = Shell.getTerminal(sess).getNumericCapability(InfoCmp.Capability.max_colors);
			return mc != null && mc.intValue() > 1;
		} catch (RuntimeException re) {
			return false;
		}
	}
	
	@Override
	public Object convert(Class<?> type, Object o) throws Exception {
		//System.out.printf("convert: %s, %s%n", type, o);
		return null;
	}

	@Override
	public CharSequence format(Object o, int mode, Converter cnvrtr) throws Exception {
		if (!(o instanceof Resource)) {
			return null;
		}
		switch (mode) {
			case INSPECT:
				return printInspect((Resource) o);
			case LINE:
				return printLine((Resource) o, "");
			case PART: {
				return printPart((Resource) o);
			}
			default:
				throw new IllegalArgumentException("illegal mode: " + mode);
		}
	}

	protected static String printPart(Resource r) {
		Map<String, String> m = getMarkup();
		StringBuilder sb = new StringBuilder();
		sb.append(m.getOrDefault("RESOURCE", "")).append(r.getName()).append(m.getOrDefault("RESET", ""));
		sb.append(" (").append(r.getResourceType().getSimpleName()).append(")");
		String val = (r instanceof ValueResource)
				? getResourceValueAsString((ValueResource) r)
				: null;
		if (val != null) {
			sb.append(": ").append(m.getOrDefault("VALUE", "")).append(val).append(m.getOrDefault("RESET", ""));
		}
		if (!r.isActive()) {
			sb.append(m.getOrDefault("PROP", "")).append(" [i]").append(m.getOrDefault("RESET", ""));
		}
		return sb.toString();
	}

	protected static String printLine(Resource r) {
		return printLine(r, null);
	}

	protected static String printLine(Resource r, String pathrel) {
		Map<String, String> m = getMarkup();
		String name = r.getName();
		if (pathrel != null) {
			if (pathrel.isEmpty() || pathrel.equals("/")) {
				name = "/" + r.getPath();
			} else {
				name = r.getPath().substring(pathrel.length());
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append(m.getOrDefault("RESOURCE", "")).append(name)
				.append(m.getOrDefault("RESET", ""));
		sb.append(" (").append(r.getResourceType().getCanonicalName()).append(")");
		if (r.isReference(true)) {
			sb.append(" ⇒ ").append(m.getOrDefault("LOCATION", ""))
					.append(r.getLocation()).append(m.getOrDefault("RESET", ""));
		}
		String val = (r instanceof ValueResource)
				? ResourceToString.getResourceValueAsString((ValueResource) r)
				: null;
		if (val != null) {
			sb.append(": ").append(m.getOrDefault("VALUE", "")).append(val)
					.append(m.getOrDefault("RESET", ""));
			long t = ((ValueResource) r).getLastUpdateTime();
			sb.append(" [").append(m.getOrDefault("TIMESTAMP", ""))
					.append(Instant.ofEpochMilli(t))
					.append(m.getOrDefault("RESET", "")).append("]");
			if (isRecorded((ValueResource) r)) {
				sb.append("[r]");
			}
		}
		if (!r.isActive()) {
			sb.append(m.getOrDefault("PROP", "")).append("[i]").append(m.getOrDefault("RESET", ""));
		}
		return sb.toString();
	}

	protected static boolean isRecorded(ValueResource r) {
		RecordedDataConfiguration rdc = null;
		if (r instanceof FloatResource) {
			rdc = ((FloatResource) r).getHistoricalData().getConfiguration();
		} else if (r instanceof BooleanResource) {
			rdc = ((BooleanResource) r).getHistoricalData().getConfiguration();
		} else if (r instanceof IntegerResource) {
			rdc = ((IntegerResource) r).getHistoricalData().getConfiguration();
		} else if (r instanceof TimeResource) {
			rdc = ((TimeResource) r).getHistoricalData().getConfiguration();
		}
		return rdc != null;
	}

	protected static String getResourceValueAsString(ValueResource r) {
		Object o = ValueResourceUtils.getValue(r);
		if (o.getClass().isArray()) {
			StringBuilder sb = new StringBuilder();
			sb.append("[");
			int l = Array.getLength(o);
			for (int i = 0; i < l; i++) {
				sb.append(Array.get(o, i));
				if (i < l - 1) {
					sb.append(", ");
				}
			}
			sb.append("]");
			return sb.toString();
		}
		return o.toString();
	}

	private String printInspect(Resource r) {
		Map<String, String> m = getMarkup();
		StringBuilder sb = new StringBuilder();
		sb.append(m.getOrDefault("RESOURCE", "")).append(r.getPath())
				.append(m.getOrDefault("RESET", "")).append("\n");
		if (r.isReference(true)) {
			sb.append("  location: ").append(m.getOrDefault("RESOURCE", ""))
					.append(r.getLocation()).append(m.getOrDefault("RESET", "")).append("\n");
		}
		sb.append("  type:     ").append(r.getResourceType().getCanonicalName()).append("\n");
		sb.append("  active:   ").append(r.isActive()).append("\n");
		if (r instanceof ValueResource) {
			String val = (r instanceof ValueResource)
					? ResourceToString.getResourceValueAsString((ValueResource) r)
					: null;
			if (val != null) {
				sb.append("  value:    ").append(m.getOrDefault("VALUE", ""))
						.append(val).append(m.getOrDefault("RESET", ""));
				long t = ((ValueResource) r).getLastUpdateTime();
				sb.append(" [").append(m.getOrDefault("TIMESTAMP", ""))
						.append(Instant.ofEpochMilli(t)).append(m.getOrDefault("RESET", "")).append("]");
				if (isRecorded((ValueResource) r)) {
					sb.append("[r]");
				}
			}
		}
		return sb.toString();
	}

}
