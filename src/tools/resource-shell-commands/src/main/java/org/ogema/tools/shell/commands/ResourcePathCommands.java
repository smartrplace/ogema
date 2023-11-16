/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.ogema.tools.shell.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.felix.gogo.jline.Shell;
import org.apache.felix.service.command.CommandSession;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.apache.felix.service.command.Process;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.utils.InfoCmp;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.model.ResourceList;
import org.ogema.core.model.ValueResource;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.recordeddata.RecordedData;
import org.ogema.core.recordeddata.RecordedDataConfiguration;
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
			"osgi.command.function=exportJson",
			"osgi.command.function=find",
			"osgi.command.function=importJson",
			"osgi.command.function=lr",
			"osgi.command.function=pcr",
			"osgi.command.function=record",
			"osgi.command.function=setvalue",
			"osgi.command.function=sr",
			"osgi.command.function=__resources",
			"osgi.command.function=printtermcap",}
)
public class ResourcePathCommands implements Application {

	ApplicationManager appman;
	BundleContext ctx;

	final static String CURRENT_RESOURCE = "currentResource";
	final static String REQUEST_PATH = "requestPath";
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
		r = getResource(sess, r, path);
		if (r == null && !"/".equals(path)) {
			sess.getConsole().printf("cr: %s: resource not found%n", path);
		} else {
			sess.put(CURRENT_RESOURCE, r);
		}
	}

	@Descriptor("Select resource")
	public Resource sr(CommandSession sess, String path) {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(sess, r, path);
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
	
	private Resource getResource(CommandSession sess, String path) {
		Resource current = (Resource) sess.get(CURRENT_RESOURCE);
		return getResource(sess, current, path);
	}

	private Resource getResource(CommandSession sess, Resource current, String path) {
		if (current == null) {
			current = new RootResource(appman);
		}
		sess.put(REQUEST_PATH, path);
		if (path == null) {
			return current;
		}
		if ("/".equals(path)) {
			//return null;
			return new RootResource(appman);
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

	private List<String> expand(CommandSession sess, String[] args, String commandName) {
		Resource cr = (Resource) sess.get(CURRENT_RESOURCE);
		if (cr == null) {
			cr = new RootResource(appman);
		}
		List<String> rval = new ArrayList<>(args.length);
		for (String a : args) {
			if (a.contains("*") || a.contains("?")) {
				if (a.contains("/")) {
					sess.getConsole().printf("%s: %s: unsupported glob expression%n", commandName, a);
					continue;
				}
				PathMatcher m = FileSystems.getDefault().getPathMatcher("glob:" + a);
				cr.getSubResources(false).stream().map(res -> Path.of(res.getName()))
						.filter(m::matches).forEach(p -> {
					rval.add(p.toString());
				});
			} else {
				rval.add(a);
			}
		}
		return rval;
	}

	public Map<String, Resource> expandAndResolve(CommandSession sess, String[] args, String commandName) {
		List<String> expandedNames = expand(sess, args, commandName);
		Map<String, Resource> rval = new LinkedHashMap<>();
		Resource current = (Resource) sess.get(CURRENT_RESOURCE);
		for (String path : expandedNames) {
			Resource r = getResource(sess, current, path);
			if (r == null) {
				sess.getConsole().printf("%s: %s: resource not found%n", commandName, path);
			} else {
				rval.put(path, r);
			}
		}
		return rval;
	}

	@Descriptor("List resources")
	public void lr(CommandSession sess,
			@Parameter(names = {"-l"}, absentValue = "false", presentValue = "true")
			@Descriptor("long format") boolean l,
			@Parameter(names = {"-s"}, absentValue = "false", presentValue = "true")
			@Descriptor("show number of subresources") boolean s,
			String... path
	) {
		Objects.requireNonNull(appman, "application manager is null");
		Objects.requireNonNull(appman.getResourceAccess(), "resource access is null");
		Process p = Process.Utils.current();
		Resource cr = (Resource) sess.get(CURRENT_RESOURCE);

		Map<String, Resource> reslist = new LinkedHashMap<>();

		List<String> pexp = expand(sess, path, "lr");
		if (pexp.isEmpty() && path.length > 0) {
			return;
		}

		for (String respath : pexp) {
			Resource res = getResource(sess, cr, respath);
			if (res == null) {
				p.out().printf("lr: %s: resource not found%n", respath);
			} else {
				reslist.put(respath, res);
			}
		}

		if (reslist.isEmpty() && path.length > 0) {
			return;
		}

		//Map<String, Resource> reslist = expandAndResolve(sess, path, "lr");
		Consumer<Resource> print = s
				? res -> {
					p.out().format("%8d ", res.getSubResources(true).size());
					printResource(p.out(), res, l);
				}
				: res -> printResource(p.out(), res, l);

		if (reslist.isEmpty()) {
			appman.getResourceAccess().getToplevelResources(null)
					.stream().sorted(RESOURCENAME_COMPARATOR)
					.forEach(print::accept);
		}

		reslist.forEach((respath, r) -> {
			if (r == null) {
				appman.getResourceAccess().getToplevelResources(null)
						.stream().sorted(RESOURCENAME_COMPARATOR)
						.forEach(print::accept);
			} else {
				if (path.length > 1 || pexp.size() > 1) {
					sess.getConsole().printf("%n%s:%n", respath);
				}
				r.getSubResources(false)
						.stream().sorted(RESOURCENAME_COMPARATOR)
						.forEach(print::accept);
			}
		});
	}

	@Descriptor("List resources")
	public void lr(CommandSession sess,
			@Parameter(names = {"-l"}, absentValue = "False", presentValue = "True")
			@Descriptor("long format") boolean l,
			@Parameter(names = {"-s"}, absentValue = "False", presentValue = "True")
			@Descriptor("show number of subresources") boolean s
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

	public Resource importJson(CommandSession sess,
			@Descriptor("import collection")
			@Parameter(names = {"-c"}, presentValue = "true", absentValue = "false")
			boolean isCollection,
			@Descriptor("import to resource")
			@Parameter(names = {"-r"}, absentValue = "")
			String resource,
			String file) throws IOException {
		Path p = Paths.get(file);
		if (!Files.exists(p)) {
			System.err.println("import: file not found: " + file);
			return null;
		}
		try (BufferedReader r = Files.newBufferedReader(p)) {
			if (resource.isEmpty()) {
				if (isCollection) {
					return appman.getSerializationManager().createResourcesFromJson(r).iterator().next();
				} else {
					return appman.getSerializationManager().createFromJson(r);
				}
			} else {
				Resource res = getResource(sess, resource);
				if (isCollection) {
					return appman.getSerializationManager().createResourcesFromJson(r, res).iterator().next();
				} else {
					return appman.getSerializationManager().createFromJson(r, res);
				}
			}
		}
	}
	
	public void exportJson(CommandSession sess,
			@Descriptor("output file (required)")
			@Parameter(names = {"-o"}, absentValue = "")
			String file,
			@Descriptor("resources to export")
			String ... paths) throws IOException {
		if ("".equals(file)) {
			sess.getConsole().println("export: must specify output file (-o)");
			return;
		}
		Map<String, Resource> res = expandAndResolve(sess, paths, "find");
		Path p = Paths.get(file);
		if (res.isEmpty()) {
			sess.getConsole().println("export: no resources found");
			return;
		}
		if (Files.exists(p) && !Files.isWritable(p)) {
			sess.getConsole().println("export: cannot write to " + p);
			return;
		}
		try (Writer w = Files.newBufferedWriter(p, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
			if (res.size() > 1) {
				appman.getSerializationManager(Integer.MAX_VALUE, true, true)
					.writeJson(w, res.values());
			} else {
				appman.getSerializationManager(Integer.MAX_VALUE, true, true)
					.writeJson(w, res.values().iterator().next());
			}
		}
		sess.getConsole().printf("%s: %d resources, %d bytes%n", p, res.size(), Files.size(p));
	}

	public Object printTermCap(CommandSession session, String cap) {
		try {
			System.out.println(Shell.getTerminal(session).getBooleanCapability(InfoCmp.Capability.valueOf(cap)));
		} catch (RuntimeException re) {
		}
		try {
			System.out.println(Shell.getTerminal(session).getNumericCapability(InfoCmp.Capability.valueOf(cap)));
		} catch (RuntimeException re) {
		}
		try {
			System.out.println(Shell.getTerminal(session).getStringCapability(InfoCmp.Capability.valueOf(cap)));
		} catch (RuntimeException re) {
		}
		return null;
	}

	public void printTermCap(CommandSession session) {
		Terminal t = Shell.getTerminal(session);
		Process p = Process.Utils.current();
		Stream.of(InfoCmp.Capability.values()).forEach(cap -> {
			Boolean bcap = t.getBooleanCapability(cap);
			Number ncap = t.getNumericCapability(cap);
			String scap = t.getStringCapability(cap);
			if (bcap != null || ncap != null || scap != null) {
				p.out().printf("%s: %s | %s | %s%n", cap, bcap, ncap, scap);
			}
		});
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
			@Parameter(names = {"-r"}, absentValue = "false", presentValue = "true") boolean rec,
			@Descriptor("resource to activate") String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(sess, base, path);
		if (res == null) {
			sess.getConsole().printf("activate: %s: resource not found%n", path);
			return;
		}
		res.activate(rec);
	}

	@Descriptor("deactivate resource")
	public void deactivate(CommandSession sess,
			@Descriptor("recursive")
			@Parameter(names = {"-r"}, absentValue = "false", presentValue = "true") boolean rec,
			@Descriptor("resource to deactivate") String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(sess, base, path);
		if (res == null) {
			sess.getConsole().printf("deactivate: %s: resource not found%n", path);
			return;
		}
		res.deactivate(rec);
	}

	@Descriptor("delete resource")
	public void delete(CommandSession sess, String path) {
		Resource base = (Resource) sess.get(CURRENT_RESOURCE);
		Resource res = getResource(sess, base, path);
		if (res == null) {
			sess.getConsole().printf("delete: %s: resource not found%n", path);
			return;
		}
		res.delete();
	}

	/**
	 * Depth first pre-order traversal of a resource graph containing all paths
	 * excluding loops (re-start of loop will not be returned). Note that the usual
	 * resource traversal (e.g. {@link Resource#getSubResources(boolean) })
	 * returns a set in which all locations are unique, whereas this traversal
	 * may return a location multiple times depending on references.
	 * 
	 */
	public static class ResourceSpliterator implements Spliterator<Resource> {

		Deque<IterationState> stack = new ArrayDeque<>();
		Set<Resource> visitedOnPath = new HashSet<>();
		int maxDepth;

		class IterationState {

			Resource res;
			List<Resource> children;
			int childIndex;

			public IterationState(Resource res, List<Resource> children, int childIndex) {
				if (!(res instanceof ResourceList)) {
					Collections.sort(children, (r1,r2) -> r1.getName().compareTo(r2.getName()));
				}
				this.res = res;
				this.children = children;
				this.childIndex = childIndex;
			}

		}
		
		public ResourceSpliterator(Resource start) {
			this(start, Integer.MAX_VALUE);
		}

		public ResourceSpliterator(Resource start, int maxDepth) {
			if (maxDepth < 0) {
				throw new IllegalArgumentException("maxDepth must be >= 0");
			}
			stack.push(new IterationState(start, start.getSubResources(false), 0));
			this.maxDepth = maxDepth;
		}

		// false => post order
		final boolean preorder = true;
		
		@Override
		public boolean tryAdvance(Consumer<? super Resource> action) {
			while (!stack.isEmpty()) {
				IterationState it = stack.peek();
				if (preorder && it.childIndex == 0) { //preorder
					action.accept(it.res);
				}
				//System.out.printf("current: %d/%d, %s%n", it.childIndex, it.children.size(), it.res.getPath());
				if (it.children.isEmpty()) {
					// only used when the starting resource is empty, otherwise
					// leaf resources are returned directly
					if (!preorder) {
						action.accept(it.res);
					}
					stack.pop();
					//System.out.println("pop (empty children)");
					visitedOnPath.remove(it.res.getLocationResource());
					return true;
				}
				if (it.childIndex == it.children.size()) {
					// return intermediate node after all children have been processed
					if (!preorder) {
						action.accept(it.res);
					}
					stack.pop();
					//System.out.println("pop");
					visitedOnPath.remove(it.res.getLocationResource());
					return true;
				}
				Resource next = it.children.get(it.childIndex++);
				if (stack.size() - 1 < maxDepth && !visitedOnPath.contains(next.getLocationResource())) {
					//System.out.println("push: " + next.getPath());
					List<Resource> nextChildren = next.getSubResources(false);
					if (nextChildren.isEmpty()) { // return leaf node directly
						action.accept(next);
						return true;
					}
					else {
						visitedOnPath.add(next.getLocationResource());
						stack.push(new IterationState(next, nextChildren, 0));
					}
				} // else: looping path, do not descend further or max depth reached
			}
			return false;
		}

		@Override
		public Spliterator<Resource> trySplit() {
			return null;
		}

		@Override
		public long estimateSize() {
			return Long.MAX_VALUE;
		}

		@Override
		public int characteristics() {
			return Spliterator.ORDERED | Spliterator.CONCURRENT;
		}

	}
	
	Stream<Resource> dfResourceStream(Resource start, int maxDepth) {
		ResourceSpliterator rs = new ResourceSpliterator(start, maxDepth);
		return StreamSupport.stream(rs, false);
	}

	@Descriptor("Find resources\n"
			+ "Regular expressions use the Perl-style Java regex syntax, matches "
			+ "can be inverted by prefixing the expression with '!', case insensitive "
			+ "matching can be enabled with '(?i)'. The command will return the "
			+ "matched resources as list, except when using -print or -exec. "
			+ "Matching is always performed in the order name, path, location, type.")
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
			@Descriptor("Execute console command for each matched resource, e.g.: \"-exec {$it delete}\", return empty list (does not work with -print).") String exec,
			@Parameter(names = {"-maxdepth"}, absentValue = "32000")
			@Descriptor("Stop traversal at 'maxdepth' levels below starting point.") int maxdepth,
			Resource res) throws ClassNotFoundException {
		if (!exec.isEmpty() && !print.isEmpty()) {
			sess.getConsole().println("find: -exec and -print are mutually exclusive.");
			return null;
		}
		Stream<Resource> s;
		/*
		if (res == null) {
			s = appman.getResourceAccess().getToplevelResources(null).stream()
					.flatMap(tr -> Stream.concat(Stream.of(tr), tr.getSubResources(true).stream()));
		} else {
			s = res.getSubResources(true).stream();
		}
		*/
		if (res == null) {
			s = appman.getResourceAccess().getToplevelResources(null).stream()
					.flatMap(tr -> dfResourceStream(tr, maxdepth));
		} else {
			s = dfResourceStream(res, maxdepth);
		}

		Resource cwr = (Resource) sess.get(CURRENT_RESOURCE);
		String pathrel = cwr != null && !cwr.getPath().isEmpty() ? "/" + cwr.getPath() : "";

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
			Class<?> c = ResourceCommands.loadResourceClass(ctx, type);//Class.forName(type);
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
			s = s.filter(r -> r instanceof ValueResource);
			if (value.startsWith("!")) {
				s = s.filter(r -> !ResourceToString.getResourceValueAsString((ValueResource) r).matches(value.substring(1)));
			} else {
				s = s.filter(r -> ResourceToString.getResourceValueAsString((ValueResource) r).matches(value));
			}
		}
		if (!print.isEmpty()) {
			Process p = Process.Utils.current();
			s.forEach(r -> p.out().println(ResourceToString.printLine(r, pathrel)));
			return Collections.emptyList();
		}
		if (!exec.isEmpty()) {
			Object outerIt = sess.get("it");
			s.forEach(r -> {
				try {
					sess.put("it", r);
					sess.execute(exec);
				} catch (Exception ex) {
					sess.put("it", outerIt);
					throw new RuntimeException(ex);
				}
			});
			sess.put("it", outerIt);
			return Collections.emptyList();
		}
		return s.collect(Collectors.toList());
	}

	@Descriptor("Find resources\n"
			+ "regular expressions use the Perl-style Java regex syntax, matches "
			+ "can be inverted by prefixing the expression with '!', case insensitive "
			+ "matching can be enabled with '(?i)'. The command will return the "
			+ "matched resources as list, except when using -print or -exec. "
			+ "Matching is always performed in the order name, path, location, type.")
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
			@Descriptor("Execute console command for each matched resource, e.g.: \"-exec {$it delete}\", return empty list (does not work with -print).") String exec,
			@Parameter(names = {"-maxdepth"}, absentValue = "32000")
			@Descriptor("Stop traversal at 'maxdepth' levels below starting point.") int maxdepth,
			String... path) throws Exception {
		/*
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(sess, r, path);
		if (path != null && !path.equals("/") && r == null) {
			sess.getConsole().printf("find: %s: resource not found.", path);
			return null;
		}
		 */

		Map<String, Resource> res = expandAndResolve(sess, path, "find");
		List<Resource> rval = new ArrayList<>();
		res.forEach((p, r) -> {
			sess.put(REQUEST_PATH, p);
			try {
				rval.addAll(find(sess, namerx, pathrx, locrx, type, time, value, print, exec, maxdepth, r));
			} catch (ClassNotFoundException cnfe) {
				sess.getConsole().printf("find: unloadable resource: %s%n", cnfe.getMessage());
			}
		});
		return rval;

		//return find(sess, namerx, pathrx, locrx, type, time, value, print, exec, r);
	}

	@Descriptor("Configure resource recording (default uses ON_VALUE_UPDATE).")
	public void record(CommandSession sess, @Descriptor("Stop recording.")
			@Parameter(names = {"-s", "--stop"}, presentValue = "true", absentValue = "false") boolean stop,
			@Descriptor("Record at fixed interval (ms).")
			@Parameter(names = {"-i"}, absentValue = "-1") long interval,
			@Descriptor("Record on value change.")
			@Parameter(names = {"-c"}, presentValue = "true", absentValue = "false") boolean onChange,
			@Descriptor("Record manually.")
			@Parameter(names = {"-m"}, presentValue = "true", absentValue = "false") boolean manual,
			@Descriptor("Show recording setting (does not modify settings).")
			@Parameter(names = {"-p"}, presentValue = "true", absentValue = "false") boolean printSettings,
			@Descriptor("Resource") String path
	) {
		Resource r = (Resource) sess.get(CURRENT_RESOURCE);
		r = getResource(sess, r, path);
		if (r == null) {
			sess.getConsole().printf("record: %s: resource not found%n", path);
		} else {
			record(sess, stop, interval, onChange, manual, printSettings, r);
		}
	}

	@Descriptor("Configure resource recording (default uses ON_VALUE_UPDATE).")
	public void record(CommandSession sess, @Descriptor("Stop recording.")
			@Parameter(names = {"-s", "--stop"}, presentValue = "true", absentValue = "false") boolean stop,
			@Descriptor("Record at fixed interval (ms).")
			@Parameter(names = {"-i"}, absentValue = "-1") long interval,
			@Descriptor("Record on value change.")
			@Parameter(names = {"-c"}, presentValue = "true", absentValue = "false") boolean onChange,
			@Descriptor("Record manually.")
			@Parameter(names = {"-m"}, presentValue = "true", absentValue = "false") boolean manual,
			@Descriptor("Show recording setting (does not modify settings).")
			@Parameter(names = {"-p"}, presentValue = "true", absentValue = "false") boolean printSettings,
			@Descriptor("Resource") Resource res
	) {
		if (onChange && interval > -1) {
			sess.getConsole().printf("record: -i and -c are mutually exclusive%n");
			return;
		}
		if (interval > -1 && interval == 0) {
			sess.getConsole().printf("record: %d is not a legal interval value.%n", interval);
			return;
		}
		RecordedData rd;
		if (res instanceof BooleanResource) {
			rd = ((BooleanResource) res).getHistoricalData();
		} else if (res instanceof FloatResource) {
			rd = ((FloatResource) res).getHistoricalData();
		} else if (res instanceof IntegerResource) {
			rd = ((IntegerResource) res).getHistoricalData();
		} else if (res instanceof TimeResource) {
			rd = ((TimeResource) res).getHistoricalData();
		} else {
			sess.getConsole().printf("record: %s: not a recordable resource type (%s)%n",
					res.getPath(), res.getResourceType().getCanonicalName());
			return;
		}
		if (printSettings) {
			RecordedDataConfiguration rdc = rd.getConfiguration();
			if (rdc == null) {
				sess.getConsole().printf("%s: OFF%n", res.getPath());
			} else {
				if (rdc.getStorageType() == RecordedDataConfiguration.StorageType.FIXED_INTERVAL) {
					sess.getConsole().printf("%s: %s, %dms%n", res.getPath(), rdc.getStorageType(), rdc.getFixedInterval());
				} else {
					sess.getConsole().printf("%s: %s%n", res.getPath(), rdc.getStorageType());
				}
			}
			return;
		}
		if (stop) {
			rd.setConfiguration(null);
		} else {
			RecordedDataConfiguration rdc = new RecordedDataConfiguration();
			if (interval > -1) {
				rdc.setStorageType(RecordedDataConfiguration.StorageType.FIXED_INTERVAL);
				rdc.setFixedInterval(interval);
			} else if (onChange) {
				rdc.setStorageType(RecordedDataConfiguration.StorageType.ON_VALUE_CHANGED);
			} else if (manual) {
				rdc.setStorageType(RecordedDataConfiguration.StorageType.MANUAL);
			} else {
				rdc.setStorageType(RecordedDataConfiguration.StorageType.ON_VALUE_UPDATE);
			}
			rd.setConfiguration(rdc);
			//sess.getConsole().printf("record: %s: configured for ON_VALUE_UPDATE recording.%n", res.getPath());
		}
	}

}
