package org.ogema.rest.servlet;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 *
 * @author jlapp
 */
@Designate(ocd = RestFilter.Config.class)
@Component(property = {
    "osgi.http.whiteboard.filter.pattern=/rest/*",
    "osgi.http.whiteboard.context.select=(osgi.http.whiteboard.context.name=org.osgi.service.http)"
},
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        configurationPid = RestFilter.PID
)
public class RestFilter implements Filter {

    public static final String PID = "org.ogema.rest.servlet.RestFilter";

    @ObjectClassDefinition(name = "OGEMA Rest Interface Servlet Filter")
    public @interface Config {

        @AttributeDefinition(description = "Response headers to set for OPTIONS method.")
        String[] options_headers() default {
            "Access-Control-Allow-Origin: *",
            "Access-Control-Allow-Methods: GET, DELETE, HEAD, OPTIONS, POST, PUT",
            "Access-Control-Allow-Credentials: true"
        };

        @AttributeDefinition(description = "Response headers to set for all methods.")
        String[] headers() default {};

    }

    Config cfg;
    Map<String, String> headers = new LinkedHashMap<>();
    Map<String, String> optionHeaders = new LinkedHashMap<>();

    @Activate
    protected void activate(Config cfg) {
        this.cfg = cfg;
        parseHeaders(cfg.headers(), headers);
        parseHeaders(cfg.options_headers(), optionHeaders);
    }

    private void parseHeaders(String[] headers, Map<String, String> headerMap) {
        if (headers == null || headers.length == 0) {
            return;
        }
        for (String header : headers) {
            String[] a = header.split(":", 2);
            if (!a[0].isEmpty()) {
                headerMap.put(a[0], a.length > 1 ? a[1].trim() : "");
            }
        }
    }

    @Override
    public void init(FilterConfig fc) throws ServletException {
    }

    private void addHeaders(Map<String, String> headers, HttpServletResponse resp) {
        for (Entry<String, String> e : headers.entrySet()) {
            resp.addHeader(e.getKey(), e.getValue());
        }
    }

    @Override
    public void doFilter(ServletRequest sr, ServletResponse sr1, FilterChain fc) throws IOException, ServletException {
        if (!(sr1 instanceof HttpServletResponse)) {
            fc.doFilter(sr, sr1);
        }
        HttpServletResponse resp = (HttpServletResponse) sr1;
        HttpServletRequest req = (HttpServletRequest) sr;
        addHeaders(headers, resp);
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            addHeaders(optionHeaders, resp);
            return;
        }
        fc.doFilter(sr, sr1);
    }

    @Override
    public void destroy() {
    }

}
