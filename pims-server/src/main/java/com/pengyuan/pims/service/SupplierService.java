package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Supplier;
import com.pengyuan.pims.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 供应商业务层
 * 支持按类型（MATERIAL/FINISHED）过滤
 */
@Service
public class SupplierService {

    private final SupplierRepository repo;
    // v5.24：全局写锁（编码生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    public SupplierService(SupplierRepository repo, WriteQueue writeQueue) {
        this.repo = repo;
        this.writeQueue = writeQueue;
    }

    /** 查询所有启用的供应商（下拉选择用） */
    public List<Supplier> listAll() { return repo.findByEnabledTrue(); }

    /** v5.27：管理列表全部（正常 + 已拉黑，排除已删除） */
    public List<Supplier> listAllManage() { return repo.findAllActiveOrBlacklisted(); }

    /** v5.27：下拉选择全部启用（含搜索用） */
    public List<Supplier> listEnabled() { return repo.findByEnabledTrue(); }

    /** 按类型查询启用的供应商（下拉选择用：MATERIAL=材料，FINISHED=成品，PROCESSOR=代工厂） */
    public List<Supplier> listByType(String type) { return repo.findByTypeAndEnabledTrue(type); }

    /** v5.27：按类型查管理列表（正常 + 已拉黑） */
    public List<Supplier> listByTypeAll(String type) { return repo.findByTypeActiveOrBlacklisted(type); }

    /** 按名称模糊搜索（管理列表：正常 + 已拉黑） */
    public List<Supplier> search(String keyword) { return repo.searchActiveOrBlacklisted(keyword); }

    /** v5.27：按名称模糊搜索（下拉选择：仅启用） */
    public List<Supplier> searchEnabled(String keyword) { return repo.findByNameContainingAndEnabledTrue(keyword); }

    /** 按类型+名称模糊搜索（管理列表：正常 + 已拉黑） */
    public List<Supplier> searchByType(String type, String keyword) {
        return repo.searchByTypeActiveOrBlacklisted(type, keyword);
    }

    /** v5.27：按类型+名称模糊搜索（下拉选择：仅启用） */
    public List<Supplier> searchByTypeEnabled(String type, String keyword) {
        return repo.findByTypeAndNameContainingAndEnabledTrue(type, keyword);
    }

    public Optional<Supplier> getById(Long id) { return repo.findById(id); }

    /** 创建供应商，自动生成编码，查重 */
    // v8.1（P0-7）：去 @Transactional，execute→executeTx（锁内包事务）
    public Supplier create(Supplier s) {
        // v5.70.1 防呆：供应商核心字段必填
        if (s.name == null || s.name.isBlank())
            throw new IllegalArgumentException("供应商名称不能为空");
        if (s.paymentTerms == null || s.paymentTerms.isBlank())
            throw new IllegalArgumentException("付款条件不能为空");
        if (s.paymentMethod == null || s.paymentMethod.isBlank())
            throw new IllegalArgumentException("付款方式不能为空");
        // v5.24：编码生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.executeTx(() -> {
            // 默认类型为材料供应商
            if (s.type == null || s.type.isBlank()) s.type = "MATERIAL";
            // 查重：名称+类型完全一致不允许重复录入
            if (s.name != null && repo.existsByNameAndType(s.name.trim(), s.type)) {
                throw new IllegalArgumentException("已存在相同名称和类型的供应商「" + s.name.trim() + "」，不允许重复录入");
            }
            if (s.code == null || s.code.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = repo.findMaxSeq("SUP-" + java.time.Year.now().getValue() + "-%");
                int year = java.time.Year.now().getValue();
                s.code = String.format("SUP-%d-%04d", year, (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return repo.save(s);
        });
    }

    /** 更新供应商信息 */
    @Transactional
    public Supplier update(Long id, Supplier s) {
        Supplier exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        exist.name = s.name;
        exist.type = s.type;
        exist.paymentTerms = s.paymentTerms;
        exist.paymentMethod = s.paymentMethod;
        exist.processingFee = s.processingFee;   // 代工厂加工费（档案维护，委外订单自动带出）
        exist.updateTime = java.time.LocalDateTime.now();
        return repo.save(exist);
    }

    /** 软删除供应商 */
    @Transactional
    public void delete(Long id) {
        Supplier s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        s.enabled = false;
        repo.save(s);
    }

    /**
     * v5.27：拉黑 / 解除拉黑（拉黑即禁用，下拉选择自动不可见；解除恢复）
     */
    @Transactional
    public Supplier setBlacklisted(Long id, boolean blacklisted) {
        Supplier s = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("供应商不存在"));
        s.blacklisted = blacklisted;
        s.enabled = !blacklisted;
        s.updateTime = java.time.LocalDateTime.now();
        return repo.save(s);
    }
}
