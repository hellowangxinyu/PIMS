package com.pengyuan.pims.service;

import com.pengyuan.pims.entity.DictItem;
import com.pengyuan.pims.repository.DictItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DictItemService {

    private final DictItemRepository repo;
    public DictItemService(DictItemRepository repo) { this.repo = repo; }

    public List<DictItem> listByType(String type) { return repo.findByTypeAndEnabledTrueOrderBySortOrderAsc(type); }
    public List<DictItem> listAll() { return repo.findByEnabledTrueOrderByTypeAscSortOrderAsc(); }

    @Transactional
    public DictItem create(DictItem item) { return repo.save(item); }

    @Transactional
    public DictItem update(Long id, DictItem item) {
        DictItem exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("字典项不存在"));
        exist.type = item.type;
        exist.value = item.value;
        exist.label = item.label;
        exist.sortOrder = item.sortOrder;
        return repo.save(exist);
    }

    @Transactional
    public void delete(Long id) { repo.deleteById(id); }
}
