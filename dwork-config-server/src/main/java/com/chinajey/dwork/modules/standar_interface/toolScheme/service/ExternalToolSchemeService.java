package com.chinajey.dwork.modules.standar_interface.toolScheme.service;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.utils.BusinessUtils;
import com.chinajey.dwork.common.utils.ExtractUtils;
import com.chinajey.dwork.modules.standar_interface.toolScheme.form.ExternalToolSchemeForm;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.common.utils.BeanConvertUtils;
import com.tengnat.dwork.common.utils.JsonUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExternalToolSchemeService {

    @Resource
    private BmfService bmfService;

    @Resource
    private BusinessUtils businessUtils;

    public static final String BMF_CLASS = "toolScheme";

    @Transactional
    public BmfObject saveOrUpdate(ExternalToolSchemeForm form) {
        BmfObject toolScheme = this.bmfService.findByUnique(BMF_CLASS, "externalDocumentCode", form.getCode());
        if (toolScheme != null) {
            form.setId(toolScheme.getPrimaryKeyValue());
        }
        check(form);
        validateAndFillData(form);
        JSONObject jsonObject = getJsonObject(form);
        return ExtractUtils.commonSaveOrUpdate(BMF_CLASS, jsonObject);
    }

    private JSONObject getJsonObject(ExternalToolSchemeForm form) {
        JSONObject jsonObject = (JSONObject) JSONObject.toJSON(form);
        jsonObject.put("externalDocumentCode", form.getCode());
        JsonUtils.jsonMergeExtFiled(form.getExtFields(), jsonObject);
        return jsonObject;
    }

    private void check(ExternalToolSchemeForm form) {
        String processRouteCode = form.getProcessRouteCode();
        String processCode = form.getProcessCode();
        BmfObject processRoute = this.businessUtils.getSyncBmfObject(BmfClassNameConst.PROCESS_ROUTE, processRouteCode);
        if (processRoute == null) {
            throw new BusinessException("工艺路线[" + processRouteCode + "]信息不存在");
        }
        List<BmfObject> processRouteDetails = processRoute.getAndRefreshList("processRouteDetails");
        if (CollectionUtils.isEmpty(processRouteDetails)) {
            throw new BusinessException("工序[" + processCode + "]不属于工艺路线[" + processRouteCode + "]");
        }
        BmfObject process = processRouteDetails.stream().filter(item -> processCode.equals(item.getString("processCode"))).findFirst().orElse(null);
        if (process == null) {
            throw new BusinessException("工序[" + processCode + "]不属于工艺路线[" + processRouteCode + "]");
        }
        // 补充主表数据
        form.setProcessRouteName(processRoute.getString("name"));
        form.setMaterialCode(processRoute.getString("materialCode"));
        form.setMaterialName(processRoute.getString("materialName"));
        form.setProcessName(process.getString("processName"));
        checkToolSchemeItems(form.getToolSchemeItems());
    }

    //校验子表（纯搬运）
    public void checkToolSchemeItems(List<ExternalToolSchemeForm.ToolSchemeItem> toolSchemeItems) {
        //校验工器具明细
        if (CollectionUtils.isNotEmpty(toolSchemeItems)) {
            //同 资源类型-器具类型-物料编码不能重复
            Map<String, List<ExternalToolSchemeForm.ToolSchemeItem>> collectMap = toolSchemeItems.stream().collect(Collectors.groupingBy(obj -> obj.getResourceType() + "-" + obj.getToolType() + "-" + obj.getMaterialCode()));
            for (List<ExternalToolSchemeForm.ToolSchemeItem> itemList : collectMap.values()) {
                if (itemList.size() > 1) {
                    throw new BusinessException("工器具方案明细,物料编码:" + itemList.get(0).getMaterialCode() + ",不能重复");
                }
            }
            for (ExternalToolSchemeForm.ToolSchemeItem toolSchemeItem : toolSchemeItems) {
                String materialCode = toolSchemeItem.getMaterialCode();
                if (StringUtils.isNotBlank(materialCode)) {
                    BmfObject material = this.businessUtils.getSyncBmfObject(BmfClassNameConst.MATERIAL, materialCode);
                    if (material == null) {
                        throw new BusinessException("工器具明细物料主数据[" + materialCode + "]不存在");
                    }
                    if (!StringUtils.equals(material.getString("type"), toolSchemeItem.getToolType())) {
                        throw new BusinessException("请选择器具类型下的物料");
                    }
                }
            }
        }
    }

    private void validateAndFillData(ExternalToolSchemeForm form) {
        this.validateCodeAndNameRepeat(form);
        // 校验子表数据及补充相关数据
        validateAndFillItemData(form);
        // 相同工艺路线编码和工序编码只能存在一个默认
        if (form.getIsDefault()) {
            BmfObject bmfObject = new BmfObject(BmfClassNameConst.TOOL_SCHEMA);
            bmfObject.put("processRouteCode", form.getProcessRouteCode());
            bmfObject.put("processCode", form.getProcessCode());
            bmfObject.put("isDefault", true);
            List<BmfObject> objectList = bmfService.find(BmfClassNameConst.TOOL_SCHEMA, bmfObject);
            if (CollectionUtils.isNotEmpty(objectList)) {
                objectList.forEach(o -> {
                    o.put("isDefault", false);
                    bmfService.updateByPrimaryKeySelective(o);
                });
            }
        }
    }

    private void validateCodeAndNameRepeat(ExternalToolSchemeForm form) {
        Long id = form.getId();
        String name = form.getName();
        BmfObject toolScheme = this.bmfService.findByUnique(BMF_CLASS, "name", name);
        if (toolScheme != null && !toolScheme.getPrimaryKeyValue().equals(id)) {
            throw new BusinessException("工器具方案名称不能重复");
        }
        Map<String, Object> map = new HashMap<>(2);
        map.put("processRouteCode", form.getProcessRouteCode());
        map.put("processCode", form.getProcessCode());
        BmfObject toolScheme2 = this.bmfService.findOne(BMF_CLASS, map);
        if (toolScheme2 != null && !toolScheme2.getPrimaryKeyValue().equals(id)) {
            throw new BusinessException("当前已存在相同工序路线编码和工序编码的工器具方案");
        }
    }

    private void validateAndFillItemData(ExternalToolSchemeForm form) {
        form.validateItems();
        List<ExternalToolSchemeForm.ToolSchemeItem> items = form.getToolSchemeItems();
        if (CollectionUtils.isEmpty(items)) {
            return;
        }
        items.stream().peek(item -> {
            if (StringUtils.isBlank(item.getResourceCode())) {
                item.setResourceCode("___");
            }
        }).collect(Collectors.groupingBy(ExternalToolSchemeForm.ToolSchemeItem::getResourceCode)).forEach((key, value) -> value.stream().map(ExternalToolSchemeForm.ToolSchemeItem::getMaterialCode).collect(Collectors.groupingBy(item -> item)).forEach((key1, materialCodes) -> {
            if (materialCodes.size() > 1) {
                throw new BusinessException("相同资源下物料不允许重复");
            }
        }));
        for (ExternalToolSchemeForm.ToolSchemeItem item : items) {
            if ("___".equals(item.getResourceCode())) {
                item.setResourceCode(null);
            }
            if (StringUtils.isNotBlank(item.getResourceCode())) {
                BmfObject resource = this.businessUtils.getSyncBmfObject(item.getResourceType(), item.getResourceCode());

                if (resource == null) {
                    throw new BusinessException("资源[" + item.getResourceCode() + "]信息不存在");
                }
                item.setResourceName(resource.getString("name"));
            }
            String materialCode = item.getMaterialCode();
            BmfObject material = this.businessUtils.getSyncBmfObject(BmfClassNameConst.MATERIAL, materialCode);
            if (material == null) {
                throw new BusinessException("物料[" + materialCode + "]信息不存在");
            }
            if (!StringUtils.equals(material.getString("type"), item.getToolType())) {
                throw new BusinessException("物料[" + materialCode + "]类型与器具类型不符");
            }
            item.setMaterialName(material.getString("name"));
            item.setSpecifications(material.getString("specifications"));
        }

        //判断是新增还是修改
        if (form.getId() != null) {
            BmfObject toolScheme = bmfService.find(BMF_CLASS, form.getId());
            if (toolScheme != null) {
                List<BmfObject> toolSchemeItems = toolScheme.getAndRefreshList("toolSchemeItems");

                Map<String, List<BmfObject>> oldToolSchemeMap = toolSchemeItems.
                        stream()
                        .collect(Collectors.groupingBy(item -> getGroupByKey(item.getString("resourceType"), item.getString("resourceCode"), item.getString("toolType"), item.getString("materialCode"))));

                Map<String, List<ExternalToolSchemeForm.ToolSchemeItem>> toolSchemeMap = items
                        .stream().
                        collect(Collectors.groupingBy(item -> getGroupByKey(item.getResourceType(), item.getResourceCode(), item.getToolType(), item.getMaterialCode())));

                Set<String> newKeys = toolSchemeMap.keySet();
                for (String newKey : newKeys) {
                    if (oldToolSchemeMap.containsKey(newKey)) {
                        List<BmfObject> bmfObjects = oldToolSchemeMap.get(newKey);
                        List<ExternalToolSchemeForm.ToolSchemeItem> newItem = toolSchemeMap.get(newKey);
                        newItem.get(0).setId(bmfObjects.get(0).getPrimaryKeyValue());
                    }
                }
            }
        }
    }

    private String getGroupByKey(String resourceType, String resourceCode, String toolType, String materialCode) {
        return getNotNullValue(resourceType) + "_" + getNotNullValue(resourceCode) + "_" + getNotNullValue(toolType) + "_" + getNotNullValue(materialCode);
    }

    private String getNotNullValue(String value) {
        return StringUtils.isBlank(value) ? "_" : value;
    }
}
