package com.dc.cloud.json.support;

import com.dc.cloud.json.runner.HandleHiveDataRunner;
import com.dc.cloud.json.support.json.HiveDataJsonHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilderFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;

import java.net.ConnectException;
import java.net.URI;

@Component
@Log4j2
public class HandleHiveJsonClient implements ApplicationEventPublisherAware, InitializingBean {

    @Value("${spring.hive.json.baseUrl:http://localhost:8111}")
    private String baseUrl;

    private  WebClient webClient = WebClient.builder().clientConnector(new ReactorClientHttpConnector(HttpClient.newConnection().compress(true))).build();

    private ApplicationEventPublisher applicationEventPublisher;

    private UriBuilderFactory uriBuilderFactory;

    public static final String REQUEST_HANDLE_JSON_URL = HandleHiveJsonClient.class.getName() + "_request_url";

    public static final String DEFAULT_JSON_CLIENT_RUNNER_KEY = HandleHiveJsonClient.class.getName() + "_runner";

    @Override
    public void afterPropertiesSet() {
        uriBuilderFactory = new DefaultUriBuilderFactory(baseUrl);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    //忽略掉报错
    //从接口获取json
    public <T extends HiveDataJsonHandler> Mono<Void> getHiveData(String jsonUrl, Class<T> jsonClass) {

        return Mono.justOrEmpty(jsonUrl)
                .filter(url -> StringUtils.hasText(jsonUrl) && jsonClass != null)
                .switchIfEmpty(Mono.defer(() -> Mono.error(new IllegalArgumentException("The json url " + jsonUrl + " must not be empty"))))
                .map(url -> uriBuilderFactory.expand(jsonUrl))
                .zipWith(Mono.just(jsonClass))
                .flatMap(this::getJsonData)
                .subscriberContext(context -> context.put(DEFAULT_JSON_CLIENT_RUNNER_KEY, this));
    }

    private <T extends HiveDataJsonHandler> Mono<Void> getJsonData(Tuple2<URI, Class<T>> tuples) {

        return webClient
                .get()
                .uri(tuples.getT1())
                .accept(MediaType.APPLICATION_JSON_UTF8)
                .exchange()
                //端口关闭错误
                .onErrorMap(e -> e instanceof ConnectException, e -> new ConnectRefusedException("Call Interface [[ " + tuples.getT1() + " ]] is connect refused", e))
                .flatMap(clientResponse -> {
                    if (!clientResponse.statusCode().is2xxSuccessful()) {
                        //访问 json url 出错了 不是200
                        return Mono.defer(()-> Mono.error(() -> new ConnectRefusedException("Call Interface [[ " + tuples.getT1() + " ]] is  go wrong, " +
                                "The error code is: [ " + clientResponse.rawStatusCode() + " ]")) );
//                        return Mono.empty();
                    }
                    return clientResponse.body(HiveDataBodyExtractor.hiveTableBodyExtractor(tuples.getT2(), applicationEventPublisher));
                })
                .doOnError(ConnectRefusedException.class, e -> log.error(e.getMessage()))
                //重试次数 可以直接注释
//                .retry(1, e -> e instanceof ConnectRefusedException)
                //当请求一个url出现异常时，并不会影响接下来的url执行
                .onErrorResume(ConnectRefusedException.class, e -> Mono.empty())
                .subscriberContext(context -> context.put(REQUEST_HANDLE_JSON_URL, tuples.getT1()));
    }

}
