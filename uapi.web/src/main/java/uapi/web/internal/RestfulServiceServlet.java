package uapi.web.internal;

import rx.Observable;
import uapi.KernelException;
import uapi.annotation.Type;
import uapi.config.annotation.Config;
import uapi.helper.ArgumentChecker;
import uapi.log.ILogger;
import uapi.service.annotation.Init;
import uapi.service.annotation.Inject;
import uapi.service.annotation.Service;
import uapi.web.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 * Generic web service url mapping: /[prefix]/[web service name]/[uri params]?[query strings]
 */
@Service(MappableHttpServlet.class)
public class RestfulServiceServlet extends MappableHttpServlet {

    private static final String DEFAULT_URI_PATTERN             = "/rest/";
    private static final String SEPARATOR_URI_QUERY_PARAM       = "\\?";
    private static final char SEPARATOR_QUERY_PARAM             = '&';
    private static final char SEPARATOR_QUERY_PARAM_KEY_VALUE   = '=';

    @Config(path=IWebConfigurableKey.WS_URI_PATTERN)
    String _uriPattern = DEFAULT_URI_PATTERN;

    @Config(path=IWebConfigurableKey.WS_DECODER)
    String _decoderName;

    @Config(path=IWebConfigurableKey.WS_ENCODER)
    String _encoderName;

    @Inject
    ILogger _logger;

    @Inject
    Map<String, IResponseWriter> _responseEncoders = new HashMap<>();

    @Inject
    Map<String, IWebService> _webSvcs = new HashMap<>();

    @Init
    public void init() {

    }

    @Override
    public String getPathPattern() {
        return this._uriPattern;
    }

    @Override
    protected void doGet(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws ServletException, IOException {
        handlRequest(request, response, HttpMethod.GET);
    }

    @Override
    protected void doPost(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws ServletException, IOException {
        handlRequest(request, response, HttpMethod.POST);
    }

    @Override
    protected void doPut(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws ServletException, IOException {
        handlRequest(request, response, HttpMethod.PUT);
    }

    @Override
    protected void doDelete(
            final HttpServletRequest request,
            final HttpServletResponse response
    ) throws ServletException, IOException {
        handlRequest(request, response, HttpMethod.DELETE);
    }

    private void handlRequest(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final HttpMethod method
    ) throws ServletException, IOException {
        UriInfo uriInfo = parseUri(request);
        String svcName = uriInfo.serviceName;
        IWebService matchedWebSvc = this._webSvcs.get(svcName);
        if (matchedWebSvc == null) {
            throw new KernelException("No web service is matched name {}", svcName);
        }
        ArgumentMeta[] argMetas = matchedWebSvc.getMethodArgumentsInfo(method);
        List<Object> argValues = new ArrayList<>();
        Observable.from(argMetas)
                .subscribe(argMeta -> {
                    ArgumentMeta.From from = argMeta.getFrom();
                    String value = null;
                    if (from == ArgumentMeta.From.Header) {
                        value = request.getHeader(((NamedArgumentMeta) argMeta).getName());
                    } else if (from == ArgumentMeta.From.Uri) {
                        value = uriInfo.uriParams.get(((IndexedArgumentMeta) argMeta).getIndex());
                    } else if (from == ArgumentMeta.From.Param) {
                        value = uriInfo.queryParams.get(((NamedArgumentMeta) argMeta).getName());
                    } else {
                        throw new KernelException("Unsupported from indication {}", from);
                    }
                    argValues.add(parseValue(value, argMeta.getType()));
                }, this._logger::error);
        Object result = matchedWebSvc.invoke(method, argValues);

        IResponseWriter encoder = this._responseEncoders.get(this._encoderName);
        if (encoder == null) {
            throw new KernelException("The response encode was not found - {}", this._encoderName);
        }
    }

    private Object parseValue(String value, String type) {
        if (Type.STRING.equals(type)) {
            return value;
        }
        if (Type.BOOLEAN.equals(type)) {
            return Boolean.parseBoolean(value);
        }
        if (Type.INTEGER.equals(type)) {
            return Integer.parseInt(value);
        }
        if (Type.LONG.equals(type)) {
            return Long.parseLong(value);
        }
        if (Type.FLOAT.equals(type)) {
            return Float.parseFloat(value);
        }
        if (Type.DOUBLE.equals(type)) {
            return Double.parseDouble(value);
        }
        throw new KernelException("Unknown type name {}", type);
    }

    private UriInfo parseUri(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.indexOf(this._uriPattern) != 0) {
            throw new KernelException("The requested URI {} is not prefixed by {}", uri, this._uriPattern);
        }
        String[] pathAndQuery = uri.split(SEPARATOR_URI_QUERY_PARAM);
        String path = pathAndQuery[0];
        String query = pathAndQuery.length >= 2 ? pathAndQuery[1] : null;

        UriInfo uriInfo = new UriInfo();
        StringBuilder buffer = new StringBuilder();
        for (int i = this._uriPattern.length() - 1; i < path.length(); i++) {
            char c = path.charAt(i);
            switch (c) {
                case '/':
                    if (uriInfo.serviceName == null) {
                        uriInfo.serviceName = buffer.toString();
                    } else {
                        uriInfo.uriParams.add(buffer.toString());
                    }
                    buffer.delete(0, buffer.length());
                    break;
                default:
                    buffer.append(c);
            }
        }
        if (buffer.length() > 0) {
            uriInfo.uriParams.add(buffer.toString());
            buffer.delete(0, buffer.length());
        }
        if (query != null) {
            String key = null;
            String value = null;
            for (int i = 0; i < query.length(); i++) {
                char c = query.charAt(i);
                switch (c) {
                    case SEPARATOR_QUERY_PARAM_KEY_VALUE:
                        key = buffer.toString();
                        buffer.delete(0, buffer.length());
                        break;
                    case SEPARATOR_QUERY_PARAM:
                        value = buffer.toString();
                        if (ArgumentChecker.isEmpty(key) || ArgumentChecker.isEmpty(value)) {
                            throw new KernelException("The query string of uri {} is invalid: empty key or value");
                        }
                        uriInfo.queryParams.put(key, value);
                        key = null;
                        value = null;
                        buffer.delete(0, buffer.length());
                        break;
                    default:
                        buffer.append(c);
                }
            }
        }

        return uriInfo;
    }

    private final class UriInfo {

        private String serviceName;
        private List<String> uriParams;
        private Map<String, String> queryParams;
    }
}
