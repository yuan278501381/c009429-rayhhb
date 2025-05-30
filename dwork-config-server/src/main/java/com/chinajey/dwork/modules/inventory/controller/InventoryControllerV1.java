package com.chinajey.dwork.modules.inventory.controller;

import com.alibaba.fastjson.JSONObject;
import com.chinajey.application.common.resp.InvokeResult;
import com.chinajey.dwork.modules.inventory.service.InventoryServiceV1;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/inventorySheetV1")
public class InventoryControllerV1 {
    @Resource
    private InventoryServiceV1 inventoryServiceV1;

    /**
     * 关闭
     */
    @PutMapping("/close/{id}")
    public InvokeResult closeInventory(@PathVariable Long id) {
        return inventoryServiceV1.closeInventory(id);
    }
    

    @DeleteMapping
    public InvokeResult delete(@RequestBody List<Long> ids) {
        return inventoryServiceV1.delete(ids);
    }

    /**
     * 下达盘点单
     */
    @PostMapping("/issued")
    public InvokeResult issued(@RequestBody JSONObject jsonObject) {
        return inventoryServiceV1.issued(jsonObject);
    }

    @PostMapping("/save")
    public InvokeResult save(@RequestBody JSONObject jsonObject) {
        return inventoryServiceV1.save(jsonObject);
    }

    @PutMapping("/update")
    public InvokeResult update(@RequestBody JSONObject jsonObject) {
        return inventoryServiceV1.update(jsonObject);
    }

    /**
     * 详情
     */
    @GetMapping("/detail/{id}")
    public InvokeResult detail(@PathVariable Long id) {
        return inventoryServiceV1.detail(id);
    }

    /**
     * 获取盘点明细的周转箱列表
     *
     * @param planId 盘点计划ID
     */
    @GetMapping("/sheetDetail/{planId}")
    public InvokeResult getSheetDetail(@PathVariable("planId") Long planId) {
        return inventoryServiceV1.getSheetDetail(planId);
    }

    @GetMapping("/export/{planId}")
    @ResponseBody
    public ResponseEntity<byte[]> export(@PathVariable("planId") Long planId) throws IOException {
        return inventoryServiceV1.export(planId);
    }

    /**
     * 盘点审核
     * @param planId 盘点计划ID
     */
    @PostMapping("/approve/{planId}")
    public InvokeResult approve(@PathVariable("planId") Long planId) {
        return inventoryServiceV1.approve(planId);
    }


    /**
     * 根据条件联动获取指定对象列表
     */
    @GetMapping("/getResource")
    public InvokeResult getLinkageQuery(@RequestParam String bmfClassName,
                                  @RequestParam(required = false) String areas,
                                  @RequestParam(required = false) String warehouse,
                                  @RequestParam(required = false) String locations) {
        return InvokeResult.success(inventoryServiceV1.getLinkageQuery(bmfClassName, areas, warehouse, locations));
    }

    /**
     * 根据条件联动获取指定对象列表(分页模糊查询名称编码)
     */
    @GetMapping("/getPageResource")
    public InvokeResult getPageLinkageQuery(@RequestParam String bmfClassName,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String areas,
                                          @RequestParam(required = false) String warehouse,
                                          @RequestParam(required = false) String locations) {
        return InvokeResult.success(inventoryServiceV1.getPageLinkageQuery(bmfClassName,page,size,keyword,areas, warehouse, locations));
    }
}
