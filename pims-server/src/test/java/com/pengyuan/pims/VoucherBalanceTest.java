package com.pengyuan.pims;

import com.pengyuan.pims.entity.Voucher;
import com.pengyuan.pims.entity.VoucherEntry;
import com.pengyuan.pims.service.VoucherService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** v8.8 核心测试 1：凭证借贷平衡 + 取号 + 记账 */
class VoucherBalanceTest extends Support {

    @Autowired VoucherService voucherService;

    private VoucherEntry entry(String code, String dr, String cr) {
        VoucherEntry e = new VoucherEntry();
        e.subjectCode = code;
        e.subjectName = code;
        e.digest = "测试";
        e.debit = dr == null ? BigDecimal.ZERO : new BigDecimal(dr);
        e.credit = cr == null ? BigDecimal.ZERO : new BigDecimal(cr);
        return e;
    }

    @Test
    void unbalancedVoucherRejected() {
        Voucher v = new Voucher();
        v.voucherDate = LocalDate.now();
        v.remark = "借100贷50";
        List<VoucherEntry> es = new ArrayList<>();
        es.add(entry("1001", "100", null));
        es.add(entry("2202", null, "50"));
        v.entries = es;
        assertThrows(Exception.class, () -> voucherService.create(v), "借贷不平必须被拒");
    }

    @Test
    void balancedVoucherCreatedWithDocNoAndPeriod() {
        Voucher v = new Voucher();
        v.voucherDate = LocalDate.now();
        v.remark = "平衡凭证";
        List<VoucherEntry> es = new ArrayList<>();
        es.add(entry("1001", "100", null));
        es.add(entry("2202", null, "100"));
        v.entries = es;
        Voucher saved = voucherService.create(v);
        assertNotNull(saved.docNo, "必须自动取号");
        assertTrue(saved.docNo.startsWith("VCH-"), "凭证号格式 VCH-YYYYMM-NNNN，实际=" + saved.docNo);
        assertNotNull(saved.period, "必须自动落期间");
        assertEquals("DRAFT", saved.status);
        // 记账
        Voucher posted = voucherService.post(saved.id, "tester");
        assertEquals("POSTED", posted.status);
    }

    @Test
    void concurrentCreateDocNosDistinct() throws Exception {
        int n = 20;
        List<Thread> threads = new ArrayList<>();
        List<String> docNos = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < n; i++) {
            Thread t = new Thread(() -> {
                Voucher v = new Voucher();
                v.voucherDate = LocalDate.now();
                v.remark = "并发";
                List<VoucherEntry> es = new ArrayList<>();
                es.add(entry("1001", "1", null));
                es.add(entry("2202", null, "1"));
                v.entries = es;
                try {
                    Voucher saved = voucherService.create(v);
                    docNos.add(saved.docNo);
                } catch (Exception e) {
                    docNos.add("ERR:" + e.getMessage());
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        assertEquals(n, docNos.size(), "全部创建成功");
        assertEquals(n, new java.util.HashSet<>(docNos).size(), "单号必须全部不重: " + docNos);
    }
}
