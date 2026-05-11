package com.alibaba.cloud.ai.copilot.store;

import com.alibaba.cloud.ai.copilot.mapper.MemoryStoreMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.harness.agent.store.BaseStore;
import io.agentscope.harness.agent.store.StoreItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessDatabaseStoreAdapter implements BaseStore {

    private final MemoryStoreMapper memoryStoreMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public StoreItem get(List<String> namespace, String key) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity entity =
                    memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);
            if (entity == null) {
                return null;
            }
            return new StoreItem(key, entity.getValue());
        } catch (JsonProcessingException e) {
            log.error("读取 Harness Store 失败: namespace={}, key={}", namespace, key, e);
            return null;
        }
    }

    @Override
    public void put(List<String> namespace, String key, Map<String, Object> value) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity existing =
                    memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);
            if (existing != null) {
                existing.setValue(value);
                existing.setUpdatedTime(LocalDateTime.now());
                memoryStoreMapper.updateById(existing);
                return;
            }

            com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity entity =
                    new com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity();
            entity.setNamespace(namespaceStr);
            entity.setKey(key);
            entity.setValue(value);
            for (String ns : namespace) {
                if (ns != null && ns.startsWith("user_")) {
                    try {
                        entity.setUserId(Long.parseLong(ns.substring("user_".length())));
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                }
            }
            memoryStoreMapper.insert(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("写入 Harness Store 失败", e);
        }
    }

    @Override
    public List<StoreItem> search(List<String> namespace, int limit, int offset) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            List<com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity> entities =
                    memoryStoreMapper.selectByNamespace(namespaceStr);
            return entities.stream()
                    .skip(Math.max(offset, 0L))
                    .limit(Math.max(limit, 0L))
                    .map(e -> new StoreItem(e.getKey(), e.getValue()))
                    .toList();
        } catch (JsonProcessingException e) {
            log.error("搜索 Harness Store 失败: namespace={}", namespace, e);
            return List.of();
        }
    }

    @Override
    public void delete(List<String> namespace, String key) {
        try {
            String namespaceStr = objectMapper.writeValueAsString(namespace);
            com.alibaba.cloud.ai.copilot.domain.entity.MemoryStoreEntity entity =
                    memoryStoreMapper.selectByNamespaceAndKey(namespaceStr, key);
            if (entity != null) {
                memoryStoreMapper.deleteById(entity.getId());
            }
        } catch (JsonProcessingException e) {
            log.error("删除 Harness Store 失败: namespace={}, key={}", namespace, key, e);
        }
    }
}
