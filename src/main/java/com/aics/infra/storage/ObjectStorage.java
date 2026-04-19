package com.aics.infra.storage;

import com.aics.config.AppProperties;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class ObjectStorage {

    private final MinioClient client;
    private final AppProperties props;

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(props.getMinio().getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder()
                        .bucket(props.getMinio().getBucket()).build());
            }
        } catch (Exception e) {
            log.warn("minio bucket ensure failed: {}", e.getMessage());
        }
    }

    public String putObject(String objectName, InputStream in, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(objectName)
                    .stream(in, size, -1)
                    .contentType(contentType)
                    .build());
            return props.getMinio().getBucket() + "/" + objectName;
        } catch (Exception e) {
            throw new RuntimeException("minio put failed: " + e.getMessage(), e);
        }
    }

    public InputStream getObject(String objectName) {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("minio get failed: " + e.getMessage(), e);
        }
    }

    public void remove(String objectName) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getMinio().getBucket())
                    .object(objectName).build());
        } catch (Exception e) {
            log.warn("minio remove failed: {}", e.getMessage());
        }
    }
}
