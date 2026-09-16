/**
 * v7.3 分桶打印 composable：入库标签"一条记录拆多桶"的对话框交互。
 * - openBucketDialog(row)：弹桶数+每桶数量表（预填均值可改），Σ≠总量时确认禁用；
 *   resolve {split, adjust}（拆分）或 null（用户选"不拆分"直接打一张）
 * - printWithBuckets(rows)：多行入口——逐行弹（各自独立桶数），全部确认后一次打印
 */
import { h, ref, reactive } from 'vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import { printLabels, computeBuckets } from '../utils/labelPrint'

export function useBucketPrint() {

  /** 单行分桶对话框：返回 Promise<{split, adjust} | null>（null=不拆分） */
  function openBucketDialog(row) {
    return new Promise(resolve => {
      const total = Number(row.qty || 0)
      const bucketCount = ref(1)
      const edited = reactive({})          // {index: qty}——用户改过的桶（锁定）
      const buckets = ref([])

      const invalid = ref(false)   // 锁定合计超总量——表格保留、守恒告警、确认禁打
      const recompute = () => {
        const arr = computeBuckets(total, bucketCount.value, { ...edited })
        if (arr == null) { invalid.value = true; return }   // 保留上次 buckets 供继续编辑
        invalid.value = false
        buckets.value = arr
      }
      recompute()
      // 桶数变化：清空锁定重算
      const onCountChange = () => {
        for (const k of Object.keys(edited)) delete edited[k]
        recompute()
      }
      const onEdit = (i, v) => {
        if (v === '' || v == null) delete edited[i]
        else edited[i] = Number(v)
        recompute()
      }

      const sum = () => buckets.value.reduce((a, v) => a + (Number(v) || 0), 0)
      const balanced = () => !invalid.value && buckets.value.length > 0 && Math.abs(sum() - total) < 0.005

      const vnode = () => h('div', { style: 'min-width:420px' }, [
        h('div', { style: 'margin-bottom:10px;color:#64748b;font-size:13px' },
          `${row.materialName || row.productName || ''} ${row.batchNo || ''} · 总量 ${total} ${row.unit || 'kg'}`),
        h('div', { style: 'display:flex;align-items:center;gap:8px;margin-bottom:10px' }, [
          h('span', { style: 'font-size:13px' }, '分桶数：'),
          h('div', { class: 'el-input-number el-input-number--small', style: 'width:120px' }, [
            h('input', {
              class: 'el-input__inner', type: 'number', min: 1, max: 200, value: bucketCount.value,
              onInput: e => { bucketCount.value = Math.max(1, Math.min(200, Number(e.target.value) || 1)); onCountChange() }
            })
          ]),
          h('span', { style: 'font-size:12px;color:#94a3b8' }, '改桶数会重置每桶的微调')
        ]),
        (invalid.value || buckets.value.length > 1) ? h('table', { class: 'el-table__inner-wrapper', style: 'width:100%;border-collapse:collapse;margin-bottom:8px;font-size:12px' },
          [
            h('thead', null, h('tr', null, [
              h('th', { style: 'border:1px solid #e2e8f0;padding:4px 8px;background:#f8fafc' }, '桶号'),
              h('th', { style: 'border:1px solid #e2e2e0;padding:4px 8px;background:#f8fafc' }, '数量'),
              h('th', { style: 'border:1px solid #e2e8f0;padding:4px 8px;background:#f8fafc' }, '说明')
            ])),
            h('tbody', null, buckets.value.map((v, i) => h('tr', { key: i }, [
              h('td', { style: 'border:1px solid #e2e8f0;padding:3px 8px;text-align:center' }, `第 ${i + 1} / ${buckets.value.length} 桶`),
              h('td', { style: 'border:1px solid #e2e8f0;padding:2px 6px' },
                h('input', {
                  type: 'number', step: '0.01', value: v,
                  style: 'width:100%;border:1px solid #dcdfe6;border-radius:4px;padding:2px 6px;font-size:12px',
                  onInput: e => onEdit(i, e.target.value)
                })),
              h('td', { style: 'border:1px solid #e2e8f0;padding:3px 8px;color:#94a3b8;font-size:11px' },
                (i in edited) ? '已手动调整（锁定）' : '自动均分（可改）')
            ])))
          ]) : null,
        (invalid.value || buckets.value.length > 1) ? h('div', {
          style: `font-size:13px;font-weight:600;color:${balanced() ? '#16a34a' : '#b56a5c'}`
        }, invalid.value
          ? `手动调整的桶合计已超过总量 ${total}，请调低后再打印（禁止打印）`
          : `各桶合计：${Math.round(sum() * 100) / 100} / 总量 ${total} ${balanced() ? '✓ 守恒' : '✗ 不守恒，禁止打印'}`) : null,

      ])

      const dlg = ElMessageBox({
        title: `分桶打印 · ${row.batchNo || ''}`,
        message: () => vnode(),
        showCancelButton: true,
        distinguishCancelAndClose: true,
        confirmButtonText: '打印',
        cancelButtonText: '不拆分（整单一张）',
        closeOnClickModal: false,
        beforeClose: (action, inst, done) => {
          if (action === 'confirm') {
            if (bucketCount.value > 1 && !balanced()) {
              ElMessage.warning('各桶合计与总量不一致，禁止打印')
              return   // 不关框
            }
            done()
          } else { done() }
        }
      }).then(() => {
        resolve(bucketCount.value > 1 ? { split: bucketCount.value, adjust: buckets.value.slice() } : null)
      }).catch(action => {
        // cancelButton=不拆分；close(X)=取消该行打印
        resolve(action === 'cancel' ? null : undefined)
      })
      return dlg
    })
  }

  /**
   * 多行打印入口：逐行弹分桶（各自独立桶数，可跳过），全部确认后一次打印。
   * rows 为空直接返回 false。
   */
  async function printWithBuckets(rows) {
    const list = (rows || []).filter(Boolean)
    if (!list.length) return false
    const records = []
    for (const row of list) {
      const r = await openBucketDialog(row)
      if (r === undefined) return false          // 用户点了 X 取消整批
      records.push(r ? { row, split: r.split, adjust: r.adjust } : { row })
    }
    return printLabels(records)
  }

  return { openBucketDialog, printWithBuckets }
}
