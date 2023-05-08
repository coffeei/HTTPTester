import org.apache.http.*;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeaderElementIterator;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class TestHttpInvoker {
    private final Logger logger = LoggerFactory.getLogger(TestHttpInvoker.class);

    public class HttpClient {
        public class HttpResult {
            Integer status;
            String body;
            Map<String, String> headers;

            public HttpResult(Integer status, String body, Map<String, String> headers) {
                this.status = status;
                this.body = body;
                this.headers = headers;
            }

            public Integer getStatus() {
                return status;
            }

            public void setStatus(Integer status) {
                this.status = status;
            }

            public String getBody() {
                return body;
            }

            public void setBody(String body) {
                this.body = body;
            }

            public Map<String, String> getHeaders() {
                return headers;
            }

            public void setHeaders(Map<String, String> headers) {
                this.headers = headers;
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                sb.append("status:").append(status).append("\r\n");
                sb.append("####################################### response headers ################################################################################################################################\r\n");
                for (String key : headers.keySet()
                ) {
                    sb.append(key).append(": ").append(headers.get(key)).append("\r\n");
                }
                sb.append("####################################### response body ###################################################################################################################################\r\n");
                sb.append(body);
                sb.append("\r\n--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------");
                return sb.toString();
            }
        }

        /**
         * 从连接池获取连接的超时时间
         */
        private int connectionRequestTimeout = 3 * 1000;

        /**
         * 链接建立的超时时间
         */
        private int connectTimeout = 10 * 1000;

        /**
         * 响应超时时间，超过此时间不再读取响应
         */
        private int socketTimeout = 20 * 1000;

        private int maxConnection = 60;

        private boolean initialized;

        private CloseableHttpClient httpClient;

        private RequestConfig getTimeOutConfig() {
            return RequestConfig.custom()
                    .setConnectTimeout(connectTimeout)
                    .setSocketTimeout(socketTimeout)
                    .setConnectionRequestTimeout(connectionRequestTimeout).build();
        }

        public HttpResult execute(URI uri, StringEntity reqEntity, Map<String, String> headers) throws IOException {

            HttpRequestBase httpRequest;
            if (reqEntity != null) {
                httpRequest = new HttpPost(uri);
                ((HttpPost) httpRequest).setEntity(reqEntity);
            } else {
                httpRequest = new HttpGet(uri);
            }
            RequestConfig requestConfig = getTimeOutConfig();
            httpRequest.setConfig(requestConfig);
            setHttpReqContext(httpRequest, headers);

            try (CloseableHttpResponse httpResponse = httpClient.execute(httpRequest)) {
                int statusCode = httpResponse.getStatusLine().getStatusCode();
                Map<String, String> respHeaders = new HashMap<>();
                for (Header header : httpResponse.getAllHeaders()
                ) {
                    respHeaders.put(header.getName(), header.getValue());
                }
                if (httpResponse.getEntity() != null) {
                    String body = EntityUtils.toString(httpResponse.getEntity(), Consts.UTF_8);
                    return new HttpResult(statusCode, body, respHeaders);
                } else {
                    return new HttpResult(statusCode, null, respHeaders);
                }
            }
        }

        private void setHttpReqContext(HttpRequestBase request, Map<String, String> headers) {
            if (headers != null) {
                System.out.println("####################################### request headers ################################################################################################################################");
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    System.out.println(entry.getKey() + ": " + entry.getValue());
                    request.addHeader(entry.getKey(), entry.getValue());
                }
            }
            //TODO 设置cookie
        }

        public void init() {
            if (!initialized) {
                PoolingHttpClientConnectionManager manager = new PoolingHttpClientConnectionManager();
                manager.setMaxTotal(maxConnection);
                manager.setDefaultMaxPerRoute(maxConnection);
                HttpRequestRetryHandler retryHandler = new DefaultHttpRequestRetryHandler(0, false);

                ConnectionKeepAliveStrategy keepAliveStrategy = new ConnectionKeepAliveStrategy() {

                    @Override
                    public long getKeepAliveDuration(HttpResponse httpResponse, HttpContext httpContext) {
                        BasicHeaderElementIterator it = new BasicHeaderElementIterator(httpResponse.headerIterator("Keep-Alive"));
                        while (it.hasNext()) {
                            HeaderElement he = it.nextElement();
                            String param = he.getName();
                            String value = he.getValue();
                            logger.info("KeepAlive===={} = {}" ,param, value);
                            if (value != null && param.equalsIgnoreCase("timeout")) {
                                try {
                                    return Long.parseLong(value) * 1000;
                                } catch (NumberFormatException ignore) {

                                }
                            }
                        }
                        return 300 * 1000;
                    }
                };
                httpClient = HttpClients.custom().setConnectionManager(manager).setRetryHandler(retryHandler).setKeepAliveStrategy(keepAliveStrategy).build();
                initialized = true;
            }
        }

        public void destroy() {
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    logger.error("Destroy lop client error", e);
                }
            }
        }

        public void setConnectionRequestTimeout(int connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public void setSocketTimeout(int socketTimeout) {
            this.socketTimeout = socketTimeout;
        }

        public void setMaxConnection(int maxConnection) {
            this.maxConnection = maxConnection;
        }

        public void setInitialized(boolean initialized) {
            this.initialized = initialized;
        }
    }

    public class HTTPReqConfigData {

        Map<String, String> headers = new HashMap<>();
        Map<String, String> prams = new HashMap<>();
        Map<String, String> cookies = new HashMap<>();
        StringBuilder requestBodyBuffer = new StringBuilder();
        String protocol = null;
        String url = null;
        List<String> servers = new ArrayList<>();

        public Map<String, String> getSysConfigs() {
            return sysConfigs;
        }

        Map<String, String> sysConfigs = new HashMap<>();

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            if (this.url != null) {
                logger.error("config duplicated,will be overwritten,old:{},new :{}", this.url, url);
            }
            this.url = url;
        }

        public void appendRequestBody(String requestBody) {
            requestBodyBuffer.append(requestBody);
        }

        public void appendOnelineRequestBody(String requestBody) {
            requestBodyBuffer.append(requestBody);
            requestBodyBuffer.append(" ");
        }

        public void setProtocol(String protocol) {
            if (this.protocol != null) {
                logger.error("config duplicated,will be overwritten,old:{},new :{}", this.protocol, protocol);
            }
            this.protocol = protocol;
        }

        public void addServer(String server) {
            this.servers.add(server);
        }

        public void addHeader(String key, String value) {
            if (this.headers.containsKey(key)) {
                logger.error("config duplicated,will be overwritten,key:{},old:{},new:{}", key, this.headers.get(key), value);
            }
            headers.put(key, value);
        }

        public void addParam(String key, String value) {
            if (this.prams.containsKey(key)) {
                logger.error("config duplicated,will be overwritten,key:{},old:{},new:{}", key, this.prams.get(key), value);
            }
            prams.put(key, value);
        }

        public void addCookie(String key, String value) {
            if (this.cookies.containsKey(key)) {
                logger.error("config duplicated,will be overwritten,key:{},old:{},new:{}", key, this.cookies.get(key), value);
            }
            cookies.put(key, value);
        }

        public void addSysConfigs(String key, String value) {
            if (this.sysConfigs.containsKey(key)) {
                logger.error("config duplicated,will be overwritten,key:{},old:{},new:{}", key, this.sysConfigs.get(key), value);
            }
            sysConfigs.put(key, value);
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public Map<String, String> getPrams() {
            return prams;
        }

        public Map<String, String> getCookies() {
            return cookies;
        }

        public String getRequestBody() {
            return requestBodyBuffer.length() > 0 ? requestBodyBuffer.toString() : null;
        }

        public String getProtocol() {
            return protocol;
        }

        public List<String> getServers() {
            return servers;
        }
    }

    public class Executor {
        ExecutorService service;
        CountDownLatch latch;
        Integer threads;
        Integer durationSecd;
        Integer intervalSecd;
        Boolean logResult;
        AtomicLong execCount = new AtomicLong(0);

        public Executor(HTTPReqConfigData configData) {
            Map<String, String> sysConfig = configData.getSysConfigs();
            threads = Integer.parseInt(sysConfig.getOrDefault("threads", "1"));
            durationSecd = Integer.parseInt(sysConfig.getOrDefault("durationSecd", "0"));
            intervalSecd = Integer.parseInt(sysConfig.getOrDefault("intervalSecd", "0"));
            logResult = Boolean.parseBoolean(sysConfig.getOrDefault("logResult", "true"));
            service = Executors.newFixedThreadPool(threads);
            latch = new CountDownLatch(threads);
        }

        public void destory() {
            if (service != null) {
                service.shutdownNow();
            }
        }

        private void doExecute(HTTPReqConfigData configData, PrintWriter writer) throws URISyntaxException, IOException {
            String requestBody = configData.getRequestBody();
            String protocol = configData.getProtocol();
            StringEntity reqEntity = null;
            if (requestBody != null) {
                reqEntity = new StringEntity(requestBody, "UTF-8");
            }
            Map<String, String> headers = configData.getHeaders();
            String url = configData.getUrl();
            Long current = System.currentTimeMillis();
            Integer total = configData.getServers().size();
            int index = 0;
            for (String server : configData.getServers()) {
                run(httpClient, protocol, server, url, headers, reqEntity, writer, ++index, total, logResult);
                execCount.incrementAndGet();
            }
            if (logResult) {
                writer.println("子线程" + Thread.currentThread().getName() +" Total:" + configData.getServers().size() + ",cost:" + (System.currentTimeMillis() - current) + " ms");
            }
        }

        public Long execute(HTTPReqConfigData configData, PrintWriter writer) {
            long startTime = System.currentTimeMillis();
            for (int i = 0; i < threads; i++) {
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            logger.info("子线程 {} 开始执行",Thread.currentThread().getName());
                            while (true) {
                                //do something
                                doExecute(configData, writer);
                                if (durationSecd == 0 || ((System.currentTimeMillis() - startTime) >= durationSecd * 1000)) {
                                    break;
                                }
                                if (intervalSecd > 0) {
                                    Thread.sleep(intervalSecd * 1000);
                                }
                            }
                            logger.info("子线程 {} 执行完成",Thread.currentThread().getName());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        } finally {
                            latch.countDown();
                        }
                    }
                };
                service.execute(runnable);
            }

            try {
                latch.await();//阻塞当前线程，直到计数器的值为0
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return execCount.get();
        }

    }

    private static Set<String> keywords = new HashSet<>(Arrays.asList
            ("#headers", "#prams", "#cookies", "#requestBody", "#requestBody[1]", "#protocol", "#url", "#servers", "#sysConfigs")
    );
    private HttpClient httpClient = null;

    private static List<String> splitFirst(String str, String splitor) {
        int idx = str.indexOf(splitor);
        String first = null;
        String second = null;
        if (idx > 0) {
            first = str.substring(0, idx);
            second = str.substring(idx + 1);
        } else {
            throw new RuntimeException("split data error:" + str);
        }
        return Arrays.asList(first, second);
    }

    private HTTPReqConfigData initWhenReading(String inputFile) throws Exception {
        File file = new File(inputFile);
        BufferedReader br = new BufferedReader(new FileReader(file));
        String data = null;
        HTTPReqConfigData configData = new HTTPReqConfigData();
        String mathchKeyWord = null;
        while ((data = br.readLine()) != null) {
            data = data.trim();
            if (keywords.contains(data)) {
                mathchKeyWord = data;
            } else if (!"".equals(data) && !data.startsWith("#")) {
                List<String> splitData = null;
                switch (mathchKeyWord) {
                    case "#headers":
                        splitData = splitFirst(data, ":");
                        configData.addHeader(splitData.get(0).trim(), splitData.get(1).trim());
                        break;
                    case "#prams":
                        splitData = splitFirst(data, ":");
                        configData.addParam(splitData.get(0).trim(), splitData.get(1).trim());
                        break;
                    case "#cookies":
                        splitData = splitFirst(data, ":");
                        configData.addCookie(splitData.get(0).trim(), splitData.get(1).trim());
                        break;
                    case "#requestBody":
                        configData.appendRequestBody(data);
                        break;
                    case "#requestBody[1]":
                        configData.appendOnelineRequestBody(data);
                        break;
                    case "#protocol":
                        configData.setProtocol(data.trim());
                        break;
                    case "#url":
                        configData.setUrl(data.trim());
                        break;
                    case "#servers":
                        configData.addServer(data.trim());
                        break;
                    case "#sysConfigs":
                        splitData = splitFirst(data, ":");
                        configData.addSysConfigs(splitData.get(0).trim(), splitData.get(1).trim());
                        break;
                    default:
                        break;
                }
            }
        }
        br.close();
        return configData;
    }

    private void run(HttpClient httpClient, String protocol, String server, String url, Map<String, String> headers, StringEntity reqEntity, PrintWriter writer, Integer index, Integer total, Boolean logResult) throws URISyntaxException, IOException {
        URI uri = new URIBuilder(protocol + server + url).setCharset(Consts.UTF_8).build();
        Long current = System.currentTimeMillis();
        HttpClient.HttpResult result = httpClient.execute(uri, reqEntity, headers);
        Long cost = (System.currentTimeMillis() - current);
        if (logResult) {
            writer.println("[" + index + "]:" + server + " cost:" + cost + " ms :" + result);
        }
        logger.error("Invoking [{}/{}] {} cost {} ms:{}", index, total, server, cost, result);
    }


    public void init() {
        synchronized (TestHttpInvoker.class) {
            if (httpClient == null) {
                httpClient = new HttpClient();
                httpClient.init();
            }
        }
    }

    public void destory() {
        if (httpClient != null) {
            httpClient.destroy();
        }
    }

    public void execute(String inputFile, String outputFile) throws Exception {
        PrintWriter writer = null;
        Executor executor = null;
        try {
            HTTPReqConfigData configData = initWhenReading(inputFile);
            if (configData.getServers().size() > 0 && configData.getUrl() != null) {
                writer = new PrintWriter(outputFile, "UTF-8");
                executor = new Executor(configData);
                long current = System.currentTimeMillis();
                Long totalCount = executor.execute(configData, writer);
                long cost= (System.currentTimeMillis() - current);
                writer.println("Total:" + totalCount + ",cost:" + cost + " ms");
                logger.error("Total:{},cost:{}ms", totalCount,cost);
            }
        } finally {
            if (executor!=null){
                executor.destory();
            }
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String inputFile = null;
        String outputFile = null;
        if (args != null && args.length >= 1) {
            inputFile = args[0];
            if (args.length >= 2) {
                outputFile = args[1];
            }
        }
        if (inputFile == null) {
            inputFile = System.getProperty("user.dir") + "/data-in.txt";
        }
        if (outputFile == null) {
            outputFile = System.getProperty("user.dir") + "/data-out.txt";
        }
        TestHttpInvoker testDeployment = new TestHttpInvoker();
        testDeployment.init();
        testDeployment.execute(inputFile, outputFile);
        testDeployment.destory();
    }
}
