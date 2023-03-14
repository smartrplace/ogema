/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.ogema.tools.shell.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.felix.gogo.jline.Shell;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.apache.felix.service.command.Process;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ValueResource;
import org.ogema.tools.resource.util.ValueResourceUtils;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

/**
 *
 * @author jlapp
 */
/*
argument completion:
complete -c ogr:cr -a '__resources'
complete -c ogr:lr -a '__resources'
complete -c ogr:find -a '__resources'
 */
@Component(
		property = {
			"osgi.command.scope=resource",
			"osgi.command.function=activate",
			"osgi.command.function=cr",
			"osgi.command.function=deactivate",
			"osgi.command.function=delete",
			"osgi.command.function=find",
			"osgi.command.function=importJson",
			"osgi.command.function=lr",
			"osgi.command.function=pcr",
			"osgi.command.function=setvalue",
			"osgi.command.function=sr",
			"osgi.command.function=__resources",}
)
public class ResourcePathCommands implements Application {

	ApplicationManager appman;
	BundleContext ctx;

	final static String CURRENT_RESOURCE = "currentResource";
	final static Comparator<Resource> RESOURCENAME_COMPARATOR = (r1, r2) -> String.CASE_INSENSITIVE_ORDER.compare(r1.getName(), r2.getName());

	@Activate
	protected void activate(BundleContext ctx) {
		this.ctx = ctx;
	}

	@Override
	public void start(ApplicationManager am) {
		this.appman = am;
	}

	@Override
	public void stop(AppStopReason asr) {
	}

	@Descriptor("Change current resource")
	public void cr(CommandSession sess, String path) {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(r, path);
		if (r == null && !"/".equals(path)) {
			sess.getConsole().printf("cr: %s: resource not found%n", path);
		} else {
			sess.put(CURRENT_RESOURCE, r);
		}
	}
	
	@Descriptor("Select resource")
	public Resource sr(CommandSession sess, String path) {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(r, path);
		if (r == null && !"/".equals(path)) {
			sess.getConsole().printf("sr: %s: resource not found%n", path);
			return null;
		} else {
			return r;
		}
	}
	
	@Descriptor("Set resource value")
	public void setValue(CommandSession sess, String path, String value) {
		Resource r = sr(sess, path);
		if (r != null) {
			if (!(r instanceof ValueResource)) {
				sess.getConsole().printf("setvalue: %s: resource is not a ValueResource%n", path);
				return;
			}
			ValueResourceUtils.setValue((ValueResource) r, value);
		}
	}

	@Descriptor("Get current resource")
	public Resource pcr(CommandSession sess) {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		return r;
	}

	private void printResource(PrintStream out, Resource r, boolean l) {
		if (l) {
			printLong(out, r);
		} else {
			printShort(out, r);
		}
	}

	private void printShort(PrintStream out, Resource r) {
		out.print(ResourceToString.printPart(r));
		out.println();
	}

	private void printLong(PrintStream out, Resource r) {
		out.print(ResourceToString.printLine(r));
		out.println();
	}

	private Resource getResource(Resource current, String path) {
		if (path == null) {
			return current;
		}
		if ("/".equals(path)) {
			return null;
		}
		if (".".equals(path)) {
			return current;
		}
		if ("..".equals(path)) {
			return current.getParent();
		}
		if (path.startsWith("/")) {
			return appman.getResourceAccess().getResource(path);
		}
		if (current == null) {
			return appman.getResourceAccess().getResource(path); //"/" + 
		} else {
			String newPath = current.getPath() + "/" + path;
			return appman.getResourceAccess().getResource(newPath);
		}
	}

	@Descriptor("List resources")
	public void lr(CommandSession sess,
			@Parameter(names = {"-l"}, absentValue = "false", presentValue = "true")
			@Descriptor("long format") boolean l,
			@Parameter(names = {"-s"}, absentValue = "false", presentValue = "true")
			@Descriptor("show size") boolean s,
			String path
	) {
		Process p = Process.Utils.current();
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		if (!path.isEmpty()) {
			r = getResource(r, path);
		}
		if (r == null && !path.isEmpty() && !(path.equals("/") || path.equals("."))) {
			p.out().printf("lr: %s: resource not found%n", path);
			return;
		}
		Consumer<Resource> print = s
				? res -> {
					p.out().format("%8d ", res.getSubResources(true).size());
					printResource(p.out(), res, l);
				}
				: res -> printResource(p.out(), res, l);
		if (r == null) {
			appman.getResourceAccess().getToplevelResources(null)
					.stream().sorted(RESOURCENAME_COMPARATOR)
					.forEach(print::accept);
		} else {
			r.getSubResources(false)
					.stream().sorted(RESOURCENAME_COMPARATOR)
					.forEach(print::accept);
		}
	}

	@Descriptor("List resources")
	public void lr(CommandSession sess,
			@Parameter(names = {"-l"}, absentValue = "False", presentValue = "True")
			@Descriptor("long format") boolean l,
			@Parameter(names = {"-s"}, absentValue = "False", presentValue = "True")
			@Descriptor("show size") boolean s
	) {
		lr(sess, l, s, ".");
	}

	private List<Candidate> __resourcesFromRoot(CommandSession session, String word) {
		word = word.startsWith("/") ? word.substring(1) : word;
		Resource pre = null;
		String toComplete;
		String prePath = "";
		if (word.lastIndexOf("/") > -1) {
			prePath = word.substring(0, word.lastIndexOf("/"));
			pre = appman.getResourceAccess().getResource(prePath);
			toComplete = word.substring(word.lastIndexOf("/") + 1);
		} else {
			toComplete = word;
		}
		List<Resource> candidateResources = pre == null
				? appman.getResourceAccess().getToplevelResources(null)
				: pre.getSubResources(false);
		List<Candidate> rval = new ArrayList<>(candidateResources.size());
		for (Resource r : candidateResources) {
			if (!toComplete.isEmpty() && !r.getName().startsWith(toComplete)) {
				continue;
			}
			String display = prePath.isEmpty()
					? r.getName()
					: prePath + "/" + r.getName();
			display += "/";
			rval.add(new Candidate("/" + display, display, null, null, "", null, false));
		}
		return rval;
	}

	private List<Candidate> __resourcesFromBase(CommandSession session, Resource base, String word) {
		word = word.startsWith("/") ? word.substring(1) : word;
		String toComplete;
		String prePath = "";
		if (word.lastIndexOf("/") > -1) {
			prePath = word.substring(0, word.lastIndexOf("/"));
			toComplete = word.substring(word.lastIndexOf("/") + 1);
		} else {
			toComplete = word;
		}
		Resource pre = base;
		if (!prePath.isEmpty()) {
			String[] a = prePath.split("/");
			for (String c : a) {
				pre = pre.getSubResource(c);
				if (pre == null) {
					return Collections.emptyList();
				}
			}
		}
		List<Resource> candidateResources = pre.getSubResources(false);
		List<Candidate> rval = new ArrayList<>(candidateResources.size());
		for (Resource r : candidateResources) {
			if (!toComplete.isEmpty() && !r.getName().startsWith(toComplete)) {
				continue;
			}
			String display = prePath.isEmpty()
					? r.getName()
					: prePath + "/" + r.getName();
			display += "/";
			rval.add(new Candidate(display, display, null, null, "", null, false));
		}
		return rval;
	}
	
	public Resource importJson(String file) throws IOException {
		Path p = Paths.get(file);
		if (!Files.exists(p)) {
			System.err.println("import: file not found: " + file);
			return null;
		}
		try ( BufferedReader r = Files.newBufferedReader(p)) {
			Resource res = appman.getSerializationManager().createFromJson(r);
			return res;
		}
	}

	public List<Candidate> __resources(CommandSession session) {
		//System.out.println(".");
		try {
			ParsedLine line = Shell.getParsedLine(session);
			String w = line.word();
			Resource r = (Resource) session.get(CURRENT_RESOURCE);
			if (w.startsWith("/") || r == null) {
				return __resourcesFromRoot(session, w);
			} else {
				return __resourcesFromBase(session, r, w);
			}
		} catch (RuntimeException re) {
			System.out.println(re);
			re.printStackTrace();
			return null;
		}
	}
	
	@Descriptor("activate resource")
	public void activate(CommandSession sess,
			@Descriptor("recursive")
			@Parameter(names = {"-r"}, absentValue = "false", presentValue = "true")
			boolean rec,
			@Descriptor("resource to activate")
			String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(base, path);
		if (res == null) {
			sess.getConsole().printf("activate: %s: resource not found%n", path);
			return;
		}
		res.activate(rec);
	}
	
	@Descriptor("deactivate resource")
	public void deactivate(CommandSession sess,
			@Descriptor("recursive")
			@Parameter(names = {"-r"}, absentValue = "false", presentValue = "true")
			boolean rec,
			@Descriptor("resource to deactivate")
			String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(base, path);
		if (res == null) {
			sess.getConsole().printf("deactivate: %s: resource not found%n", path);
			return;
		}
		res.deactivate(rec);
	}
	
	@Descriptor("delete resource")
	public void delete(CommandSession sess, String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(base, path);
		if (res == null) {
			sess.getConsole().printf("delete: %s: resource not found%n", path);
			return;
		}
		res.delete();
	}

	@Descriptor("Find resources\n"
			+ "Regular expressions use the Perl-style Java regex syntax, matches "
			+ "can be inverted by prefixing the expression with '!', case insensitive "
			+ "matching can be enabled with '(?i)'. The command will return the "
			+ "matched resources as list, except when using -print or -exec. "
			+ "Matching is always done in the order listed here, parameter order "
			+ "on the command line does not matter.")
	public List<Resource> find(CommandSession sess,
			@Parameter(names = {"-name"}, absentValue = "")
			@Descriptor("Match name with regular expression.") String namerx,
			@Parameter(names = {"-path"}, absentValue = "")
			@Descriptor("Match path with regular expression.") String pathrx,
			@Parameter(names = {"-loc"}, absentValue = "")
			@Descriptor("Match location with regular expression.") String locrx,
			@Parameter(names = {"-type"}, absentValue = "")
			@Descriptor("Return only resources that are assignable to this type") String type,
			@Parameter(names = {"-time"}, absentValue = "")
			@Descriptor("Return only ValueResources that have been updated within the given ISO8601 duration, "
					+ "prefix with '+' to get older resources.") String time,
			@Parameter(names = {"-value"}, absentValue = "")
			@Descriptor("Match string representation of resources value with regular expression.") String value,
			@Parameter(names = {"-print"}, absentValue = "", presentValue = "print")
			@Descriptor("Print found resources with path, return empty list (does not work with -exec).") String print,
			@Parameter(names = {"-exec"}, absentValue = "")
			@Descriptor("Execute console command for each matched resource, e.g.: \"-exec '$it delete'\", return empty list (does not work with -print).") String exec,
			Resource res) throws ClassNotFoundException {
		if (!exec.isEmpty() && !print.isEmpty()) {
			sess.getConsole().println("find: -exec and -print are mutually exclusive.");
			return null;
		}
		Stream<Resource> s;
		if (res == null) {
			s = appman.getResourceAccess().getToplevelResources(null).stream()
					.flatMap(tr -> Stream.concat(Stream.of(tr), tr.getSubResources(true).stream()));
		} else {
			s = res.getSubResources(true).stream();
		}
		String pathrel = res == null
				? ""
				: res.getPath() + "/";
		if (!namerx.isEmpty()) {
			if (namerx.startsWith("!")) {
				s = s.filter(r -> !r.getName().matches(namerx.substring(1)));
			} else {
				s = s.filter(r -> r.getName().matches(namerx));
			}
		}
		if (!pathrx.isEmpty()) {
			Function<String, String> relativePath = p -> p.substring(pathrel.length());
			if (pathrx.startsWith("!")) {
				s = s.filter(r -> !relativePath.apply(r.getPath()).matches(pathrx.substring(1)));
			} else {
				s = s.filter(r -> relativePath.apply(r.getPath()).matches(pathrx));
			}
		}
		if (!locrx.isEmpty()) {
			if (locrx.startsWith("!")) {
				s = s.filter(r -> !r.getLocation().matches(locrx.substring(1)));
			} else {
				s = s.filter(r -> r.getLocation().matches(locrx));
			}
		}
		if (!type.isEmpty()) {
			Class c = ResourceCommands.loadResourceClass(ctx, type);//Class.forName(type);
			if (c == null) {
				sess.getConsole().println("find: type not found: " + type);
				return null;
			}
			s = s.filter(r -> c.isAssignableFrom(r.getResourceType()));
		}
		if (!time.isEmpty()) {
			boolean inv = time.startsWith("+");
			if (time.startsWith("+")) {
				time = time.substring(1);
			}
			Duration d = Duration.parse(time);
			long now = System.currentTimeMillis();
			long test = now - d.toMillis();
			s = s.filter(r -> r instanceof ValueResource)
					.filter(r -> inv ^ (((ValueResource) r).getLastUpdateTime() >= test));
		}
		if (!value.isEmpty()) {
			s = s.filter(r -> r instanceof ValueResource)
					.filter(r -> ResourceToString.getResourceValueAsString((ValueResource) r).matches(value));
		}
		if (!print.isEmpty()) {
			Process p = Process.Utils.current();
			s.forEach(r -> p.out().println(ResourceToString.printLine(r, pathrel)));
			return Collections.emptyList();
		}
		if (!exec.isEmpty()) {
			s.forEach(r -> {
				Object outerIt = sess.get("it");
				try {
					sess.put("it", r);
					sess.execute(exec);
				} catch (Exception ex) {
					throw new RuntimeException(ex);
				} finally {
					sess.put("it", outerIt);
				}
			});
			return Collections.emptyList();
		}
		return s.collect(Collectors.toList());
	}

	@Descriptor("Find resources\n"
			+ "regular expressions use the Perl-style Java regex syntax, matches "
			+ "can be inverted by prefixing the expression with '!', case insensitive "
			+ "matching can be enabled with '(?i)'. The command will return the "
			+ "matched resources as list, except when using -print or -exec. "
			+ "Matching is always done in the order listed here, parameter order "
			+ "on the command line does not matter.")
	public List<Resource> find(CommandSession sess,
			@Parameter(names = {"-name"}, absentValue = "")
			@Descriptor("Match name with regular expression.") String namerx,
			@Parameter(names = {"-path"}, absentValue = "")
			@Descriptor("Match path with regular expression.") String pathrx,
			@Parameter(names = {"-loc"}, absentValue = "")
			@Descriptor("Match location with regular expression.") String locrx,
			@Parameter(names = {"-type"}, absentValue = "")
			@Descriptor("Return only resources that are assignable to this type") String type,
			@Parameter(names = {"-time"}, absentValue = "")
			@Descriptor("Return only ValueResources that have been updated within the given ISO8601 duration, "
					+ "prefix with '+' to get older resources.") String time,
			@Parameter(names = {"-value"}, absentValue = "")
			@Descriptor("Match string representation of resources value with regular expression.") String value,
			@Parameter(names = {"-print"}, absentValue = "", presentValue = "print")
			@Descriptor("Print found resources with path, return empty list (does not work with -exec).") String print,
			@Parameter(names = {"-exec"}, absentValue = "")
			@Descriptor("Execute console command for each matched resource, e.g.: \"-exec '$it delete'\", return empty list (does not work with -print).") String exec,
			String path) throws Exception {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(r, path);
		if (path != null && r == null) {
			sess.getConsole().printf("find: %s: resource not found.", path);
			return null;
		}
		return find(sess, namerx, pathrx, locrx, type, time, value, print, exec, r);
	}

}
