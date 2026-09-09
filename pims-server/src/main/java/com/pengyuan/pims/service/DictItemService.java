package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.DictItem;
import com.pengyuan.pims.repository.DictItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DictItemService {

    private final com.pengyuan.pims.common.WriteQueue writeQueue;   // v8.8（A2）：写路径收口
    private final DictItemRepository repo;
    public DictItemService(DictItemRepository repo, com.pengyuan.pims.common.WriteQueue writeQueue) {
        this.writeQueue = writeQueue; this.repo = repo; }

    public List<DictItem> listByType(String type) { return repo.findByTypeAndEnabledTrueOrderBySortOrderAsc(type); }
    public List<DictItem> listAll() { return repo.findByEnabledTrueOrderByTypeAscSortOrderAsc(); }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public DictItem create(DictItem item) { return repo.save(item); }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public DictItem update(Long id, DictItem item) {
        return writeQueue.executeTx(() -> {
            DictItem exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("字典项不存在"));
            exist.type = item.type;
            exist.value = item.value;
            exist.label = item.label;
            exist.sortOrder = item.sortOrder;
            return repo.save(exist);
    
        });
    }

    // v8.8（A2）：去 @Transactional——写路径已收口 executeTx（锁内包事务）
    public void delete(Long id) { repo.deleteById(id); }
}
