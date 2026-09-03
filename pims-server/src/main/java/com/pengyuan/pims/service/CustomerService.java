package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Customer;
import com.pengyuan.pims.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository repo;
    // v5.24：全局写锁（编码生成+保存共用，防并发撞号）
    private final WriteQueue writeQueue;
    public CustomerService(CustomerRepository repo, WriteQueue writeQueue) {
        this.repo = repo;
        this.writeQueue = writeQueue;
    }

    /** v5.27：管理列表——正常 + 拉黑可见，已删除（enabled=false 且未拉黑）不可见 */
    public List<Customer> listAll() { return repo.findAllActiveOrBlacklisted(); }
    public List<Customer> search(String keyword) { return repo.findByNameContainingActiveOrBlacklisted(keyword); }

    /** v5.27：下拉选择仅启用（拉黑/删除的自动不可见） */
    public List<Customer> listEnabled() { return repo.findByEnabledTrue(); }

    /** v5.27：下拉选择按名称搜索（仅启用） */
    public List<Customer> searchEnabled(String keyword) { return repo.findByNameContainingAndEnabledTrue(keyword); }

    public Optional<Customer> getById(Long id) { return repo.findById(id); }

    @Transactional
    public Customer create(Customer c) {
                // v5.24：编码生成+保存整体排队（WriteQueue 全局锁），防并发撞号
        return writeQueue.execute(() -> {
            if (c.name != null && repo.existsByName(c.name.trim())) {
                throw new IllegalArgumentException("已存在同名客户「" + c.name.trim() + "」，不允许重复录入");
            }
            if (c.code == null || c.code.isBlank()) {
                // v5.24：按最大序号+1（count 会删除错位且并发撞号）
                Integer maxSeq = repo.findMaxSeq("CUS-" + java.time.Year.now().getValue() + "-%");
                int year = java.time.Year.now().getValue();
                c.code = String.format("CUS-%d-%04d", year, (maxSeq == null ? 0 : maxSeq) + 1);
            }
            return repo.save(c);
        });
    }

    @Transactional
    public Customer update(Long id, Customer c) {
        Customer exist = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        exist.name = c.name;
        exist.address = c.address;
        exist.contactPerson = c.contactPerson;
        exist.contactPhone = c.contactPhone;
        exist.abcLevel = c.abcLevel;
        exist.paymentTerms = c.paymentTerms;
        exist.paymentMethod = c.paymentMethod;
        exist.remark = c.remark;
        // v5.27：合同需方（甲方）信息
        exist.legalPerson = c.legalPerson;
        exist.bankName = c.bankName;
        exist.bankAccount = c.bankAccount;
        exist.taxNo = c.taxNo;
        // v5.52：信用额度（空/0=不限额）
        exist.creditLimit = c.creditLimit;
        exist.updateTime = java.time.LocalDateTime.now();
        return repo.save(exist);
    }

    @Transactional
    public void delete(Long id) {
        Customer c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        c.enabled = false;
        repo.save(c);
    }

    /**
     * v5.27：拉黑 / 解除拉黑（拉黑即禁用，销售下单选择客户时自动不可见；解除恢复）
     */
    @Transactional
    public Customer setBlacklisted(Long id, boolean blacklisted) {
        Customer c = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("客户不存在"));
        c.blacklisted = blacklisted;
        c.enabled = !blacklisted;
        c.updateTime = java.time.LocalDateTime.now();
        return repo.save(c);
    }
}
