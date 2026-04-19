package com.aics.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {

    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusClient(AppProperties props) {
        ConnectParam param = ConnectParam.newBuilder()
                .withHost(props.getMilvus().getHost())
                .withPort(props.getMilvus().getPort())
                .build();
        return new MilvusServiceClient(param);
    }
}
