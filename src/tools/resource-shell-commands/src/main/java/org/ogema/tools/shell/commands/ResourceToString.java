package org.ogema.tools.shell.commands;

import java.lang.reflect.Array;
import java.time.Instant;
import org.apache.felix.service.command.Converter;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
import org.ogema.tools.resource.util.ValueResourceUtils;
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

	public static String RESET = ANSI_RESET;
	public static String TIMESTAMP = ANSI_GREEN;
	public static String VALUE = ANSI_YELLOW;
	public static String RESOURCE = "\033[38;5;27m";//ANSI_BLUE;
	public static String LOCATION = "\033[38;5;27m"+ANSI_DEC_ITALIC;//ANSI_BLUE;
	public static String PROP = ANSI_CYAN;

	public ResourceToString() {
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
				return printLine((Resource) o);
			case PART: {
				return printPart((Resource) o);
			}
			default:
				throw new IllegalArgumentException("illegal mode: " + mode);
		}
	}

	protected static String printPart(Resource r) {
		StringBuilder sb = new StringBuilder();
		sb.append(RESOURCE).append(r.getName()).append(RESET);
		sb.append(" (").append(r.getResourceType().getSimpleName()).append(")");
		String val = (r instanceof ValueResource)
				? getResourceValueAsString((ValueResource) r)
				: null;
		if (val != null) {
			sb.append(": ").append(VALUE).append(val).append(RESET);
		}
		if (!r.isActive()) {
			sb.append(PROP).append(" [i]").append(RESET);
		}
		return sb.toString();
	}

	protected static String printLine(Resource r) {
		return printLine(r, null);
	}

	protected static String printLine(Resource r, String pathrel) {
		String name = r.getName();
		if (pathrel != null) {
			name = r.getPath().substring(pathrel.length());
		}
		StringBuilder sb = new StringBuilder();
		sb.append(RESOURCE).append(name).append(RESET);
		sb.append(" (").append(r.getResourceType().getCanonicalName()).append(")");
		if (r.isReference(true)) {
			sb.append(" ⇒ ").append(LOCATION).append(r.getLocation()).append(RESET);
		}
		String val = (r instanceof ValueResource)
				? ResourceToString.getResourceValueAsString((ValueResource) r)
				: null;
		if (val != null) {
			sb.append(": ").append(VALUE).append(val).append(RESET);
			long t = ((ValueResource) r).getLastUpdateTime();
			sb.append(" [").append(TIMESTAMP).append(Instant.ofEpochMilli(t)).append(RESET).append("]");
			if (isRecorded((ValueResource) r)) {
				sb.append("[r]");
			}
		}
		if (!r.isActive()) {
			sb.append(PROP).append("[i]").append(RESET);
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
		StringBuilder sb = new StringBuilder();
		sb.append(RESOURCE).append(r.getPath()).append(RESET).append("\n");
		if (r.isReference(true)) {
			sb.append("  location: ").append(RESOURCE).append(r.getLocation()).append(RESET).append("\n");
		}
		sb.append("  type:     ").append(r.getResourceType().getCanonicalName()).append("\n");
		sb.append("  active:   ").append(r.isActive()).append("\n");
		if (r instanceof ValueResource) {
			String val = (r instanceof ValueResource)
					? ResourceToString.getResourceValueAsString((ValueResource) r)
					: null;
			if (val != null) {
				sb.append("  value:    ").append(VALUE).append(val).append(RESET);
				long t = ((ValueResource) r).getLastUpdateTime();
				sb.append(" [").append(TIMESTAMP).append(Instant.ofEpochMilli(t)).append(RESET).append("]");
				if (isRecorded((ValueResource) r)) {
					sb.append("[r]");
				}
			}
		}
		return sb.toString();
	}

}
