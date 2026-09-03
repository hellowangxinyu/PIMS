package com.pengyuan.pims.service;

import com.pengyuan.pims.common.WriteQueue;
import com.pengyuan.pims.entity.Employee;
import com.pengyuan.pims.repository.EmployeeRepository;
import com.pengyuan.pims.repository.SalaryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 员工档案（v5.62 工资核算）：dept 决定工资计提借方科目 */
@Service
public class EmployeeService {

    private static final Set<String> DEPTS = Set.of("PRODUCTION", "SALES", "ADMIN", "TECH", "QC", "OTHER");

    private final EmployeeRepository repo;
    private final SalaryItemRepository salaryItemRepo;
    private final WriteQueue writeQueue;

    public EmployeeService(EmployeeRepository repo, SalaryItemRepository salaryItemRepo, WriteQueue writeQueue) {
        this.repo = repo;
        this.salaryItemRepo = salaryItemRepo;
        this.writeQueue = writeQueue;
    }

    public List<Employee> list() { return repo.findAllByOrderByIdAsc(); }

    @Transactional
    public Employee create(Employee e) {
        validate(e);
        return writeQueue.execute(() -> repo.save(e));
    }

    @Transactional
    public Employee update(Long id, Employee in) {
        Employee e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        validate(in);
        e.name = in.name;
        e.dept = in.dept;
        e.position = in.position;
        e.hireDate = in.hireDate;
        e.leaveDate = in.leaveDate;
        e.baseSalary = in.baseSalary;
        e.bankCard = in.bankCard;
        e.phone = in.phone;
        e.remark = in.remark;
        e.updateTime = LocalDateTime.now();
        return repo.save(e);
    }

    /** 员工停用/启用（离职走 leaveDate，停用后工资单不再带出） */
    @Transactional
    public Employee toggle(Long id) {
        Employee e = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("员工不存在"));
        e.status = "ENABLED".equals(e.status) ? "DISABLED" : "ENABLED";
        e.updateTime = LocalDateTime.now();
        return repo.save(e);
    }

    @Transactional
    public void delete(Long id) {
        if (salaryItemRepo.existsByEmployeeId(id)) {
            throw new IllegalArgumentException("该员工已出现在工资单中，不能删除（请改用离职/停用）");
        }
        writeQueue.execute(() -> repo.deleteById(id));
    }

    private void validate(Employee e) {
        if (e.name == null || e.name.isBlank()) throw new IllegalArgumentException("姓名不能为空");
        if (!DEPTS.contains(e.dept)) throw new IllegalArgumentException("部门不合法");
        if (e.baseSalary == null || e.baseSalary.compareTo(java.math.BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("基本工资不能为负");
        }
    }
}
