package com.chinajey.dwork.modules.enhance;

import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.service.BmfService;
import com.chinajay.virgo.bmf.sql.*;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.application.common.utils.ValueUtil;
import com.tengnat.dwork.common.constant.BmfAttributeConst;
import com.tengnat.dwork.common.constant.BmfClassNameConst;
import com.tengnat.dwork.external.unqualifiedWipReview.UnqualifiedWipReviewProvider;
import com.tengnat.dwork.modules.basic_data.mapper.BusinessPartnerMapper;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *  王统2025年4月25日上午11.50分说去迈金搬过来标准默认的发起不合格评审的逻辑
 * @author erton.bi
 */
@Component
public class ExternalUnqService implements UnqualifiedWipReviewProvider {

    @Resource
    BmfService bmfService;

    @Resource
    SceneGroovyService sceneGroovyService;

    @Resource
    BusinessPartnerMapper businessPartnerMapper;

    @Override
    public void startGnTask(String code) {
        BmfObject unqualifiedWipReview = bmfService.findByUnique(BmfClassNameConst.UNQUALIFIED_WIP_REVIEW, "code", code);
        if (unqualifiedWipReview == null) {
            throw new BusinessException("不合格评审单不存在,编码:" + code);
        }

        Map<String, List<BmfObject>> details = unqualifiedWipReview.getAndRefreshList("unqualifiedWipReviewDetails").stream()
                .collect(Collectors.groupingBy(item -> ValueUtil.toStr(item.getString("palletCode"))));

        String materialCode = unqualifiedWipReview.getString("materialCode");
        String materialName = unqualifiedWipReview.getString("materialName");

        JSONObject gn3503 = new JSONObject();
        gn3503.put("dataSourceCode", unqualifiedWipReview.getString("code"));
        gn3503.put("ext_code", unqualifiedWipReview.getString("code"));
        gn3503.put("ext_inspection_type", unqualifiedWipReview.getAndRefreshBmfObject("inspectionType").getString("name"));
        gn3503.put("ext_source_type", unqualifiedWipReview.getString("sourceType"));
        gn3503.put("ext_source_code", unqualifiedWipReview.getString("sourceCode"));
        gn3503.put("ext_inspection_sheet_code", unqualifiedWipReview.getString("inspectionSheetCode"));
        gn3503.put("ext_process_code", unqualifiedWipReview.getString("processCode"));
        gn3503.put("ext_process_name", unqualifiedWipReview.getString("processName"));
        gn3503.put("ext_process_no", unqualifiedWipReview.getString("processNo"));
        gn3503.put("ext_business_partner_code", unqualifiedWipReview.getString("ext_business_partner_code"));
        gn3503.put("ext_business_partner_name", unqualifiedWipReview.getString("ext_business_partner_name"));
        gn3503.put("ext_material_code", materialCode);
        gn3503.put("ext_material_name", materialName);
        gn3503.put("ext_quantity", unqualifiedWipReview.getBigDecimal("reviewQuantity"));
        gn3503.put("ext_send_resource_code", unqualifiedWipReview.getString("ownerCode"));
        gn3503.put("ext_send_resource_name", unqualifiedWipReview.getString("ownerName"));
        gn3503.put("ext_start_date", unqualifiedWipReview.getDate("createTime"));
        gn3503.put("ext_status", unqualifiedWipReview.getString("status"));
        gn3503.put("ext_inspection_result", unqualifiedWipReview.getString("judgementResult"));
        gn3503.put("ext_inspection_stage", unqualifiedWipReview.getAndRefreshBmfObject("inspectionType") == null ? null : unqualifiedWipReview.getBmfObject("inspectionType").getString("inspectionStage"));

        List<JSONObject> gn3503TaskList = new ArrayList<>();
        List<JSONObject> gn3503PassBoxList = new ArrayList<>();
        for (Map.Entry<String, List<BmfObject>> entry : details.entrySet()) {
            List<BmfObject> detailsList = entry.getValue();
            String palletCode = entry.getKey();
            JSONObject gn3503TaskItem = new JSONObject();
            if (StringUtils.isNotBlank(palletCode)) {
                gn3503TaskItem.put("palletCode", palletCode);
                gn3503TaskItem.put("palletName", ValueUtil.toStr(detailsList.get(0).getString("palletName")));
                gn3503TaskItem.put("thisPallet", true);
            }
            gn3503TaskItem.put("materialCode", materialCode);
            gn3503TaskItem.put("materialName", materialName);
            gn3503TaskItem.put("quantityUnit", detailsList.get(0).get("unit"));
            gn3503TaskItem.put("submit", false);
            gn3503TaskList.add(gn3503TaskItem);

            List<JSONObject> gn3503PassBoxItems = new ArrayList<>();
            for (BmfObject item : detailsList) {
                JSONObject gn3503PassBoxItem = new JSONObject();
                if (StringUtils.isNotBlank(palletCode)) {
                    gn3503PassBoxItem.put("palletCode", palletCode);
                    gn3503PassBoxItem.put("palletName", ValueUtil.toStr(detailsList.get(0).getString("palletName")));
                    gn3503PassBoxItem.put("thisPallet", true);
                }
                BmfObject passBoxReal = this.bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, "code", item.getString("passBoxRealCode"));
                JSONObject jsonObject1 = (JSONObject) JSONObject.toJSON(passBoxReal);
                jsonObject1.remove("id");
                jsonObject1.put("submit", false);
                gn3503PassBoxItem.putAll(jsonObject1);
                gn3503PassBoxItems.add(gn3503PassBoxItem);
            }
            gn3503PassBoxList.addAll(gn3503PassBoxItems);
        }

        gn3503.put("tasks", gn3503TaskList);
        gn3503.put("passBoxes", gn3503PassBoxList);
        sceneGroovyService.buzSceneStart("GN3503", gn3503);
    }

    @Override
    public Page<BmfObject> getResponsibleDeptPage(String keyword, String responsibilityClassification, String code, Integer page, Integer size) {
        if (StringUtils.isBlank(responsibilityClassification)) {
            throw new BusinessException("责任分类为空");
        }
        if (StringUtils.isBlank(code)) {
            throw new BusinessException("不合格评审单编码不能为空");
        }

        Pageable pageable = PageRequest.of(page, size);
        List<CombRestriction> combRestrictions = new ArrayList<>();
        Page<BmfObject> result = null;

        if (StringUtils.isNotBlank(keyword)) {
            List<Restriction> restrictions = new ArrayList<>();
            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
            restrictions.add(Restriction.builder()
                    .conjunction(Conjunction.AND)
                    .operationType(OperationType.LIKE)
                    .attributeName("name")
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
            restrictions.add(Restriction.builder()
                    .conjunction(Conjunction.OR)
                    .operationType(OperationType.LIKE)
                    .attributeName("code")
                    .values(Collections.singletonList("%" + keyword + "%"))
                    .build());
            keywordCombRestriction.setRestrictions(restrictions);
            combRestrictions.add(keywordCombRestriction);
        }


        //内部责任
        if ("internal".equals(responsibilityClassification)) {
//            List<Restriction> restrictions = new ArrayList<>();
//            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
//            restrictions.add(Restriction.builder()
//                    .conjunction(Conjunction.AND)
//                    .operationType(OperationType.EQUAL)
//                    .attributeName("dimensionCode")
//                    .values(Collections.singletonList("workshopSection"))
//                    .build());
//
//            List<Object> codes = businessPartnerMapper.selectBusinessPartnerCode();
//            //去除业务伙伴重复编码
//            if (codes.size() > 0) {
//                restrictions.add(Restriction.builder()
//                        .conjunction(Conjunction.AND)
//                        .operationType(OperationType.NOT_IN)
//                        .attributeName("code")
//                        .values(codes)
//                        .build());
//            }
//            keywordCombRestriction.setRestrictions(restrictions);
//            combRestrictions.add(keywordCombRestriction);

//            //查询条件
//            Where where = Where.builder()
//                    .combRestrictions(combRestrictions)
//                    .order(Order.builder()
//                            .sortFields(Collections.singletonList(
//                                    SortField.builder()
//                                            .bmfClassName("costCenter")
//                                            .bmfAttributeName(BmfAttributeConst.ID)
//                                            .fieldSort(FieldSort.DESC)
//                                            .build()
//                            )).build())
//                    .build();
//            result = this.bmfService.findPage("costCenter", where, pageable);
            //查询条件
            Where where = Where.builder()
                    .combRestrictions(combRestrictions)
                    .order(Order.builder()
                            .sortFields(Collections.singletonList(
                                    SortField.builder()
                                            .bmfClassName("department")
                                            .bmfAttributeName(BmfAttributeConst.ID)
                                            .fieldSort(FieldSort.DESC)
                                            .build()
                            )).build())
                    .build();
            result = this.bmfService.findPage("department", where, pageable);
        }
        if ("external".equals(responsibilityClassification)) {
            CombRestriction keysStatus = CombRestriction.builder().conjunction(Conjunction.AND).restrictions(
                    Collections.singletonList(Restriction.builder()
                            .conjunction(Conjunction.AND)
                            .operationType(OperationType.EQUAL)
                            .attributeName("status")
                            .values(Collections.singletonList(true))
                            .build())
            ).build();
            combRestrictions.add(keysStatus);
            //查询不合格评审单号
            BmfObject unqualifiedWipReview = bmfService.findByUnique("unqualifiedWipReview", "code", code);
            BmfObject inspectionType = unqualifiedWipReview.getAndRefreshBmfObject("inspectionType");
            String inspectionTypeCode = inspectionType.getString("code");

            List<Restriction> restrictions = new ArrayList<>();
            CombRestriction keywordCombRestriction = CombRestriction.builder().conjunction(Conjunction.AND).build();
            //来料检
            if ("LX00004".equals(inspectionTypeCode)) {
                restrictions.add(Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .operationType(OperationType.EQUAL)
                        .attributeName("type")
                        .values(Collections.singletonList("supplier"))
                        .build());
            }
            //客户退货检
            if ("LX00009".equals(inspectionTypeCode)) {
                restrictions.add(Restriction.builder()
                        .conjunction(Conjunction.AND)
                        .operationType(OperationType.EQUAL)
                        .attributeName("type")
                        .values(Collections.singletonList("customer"))
                        .build());
            }
            //外部责任默认不合格评审的业务伙伴  威铝的
            restrictions.add(Restriction.builder()
                    .conjunction(Conjunction.AND)
                    .operationType(OperationType.EQUAL)
                    .attributeName("code")
                    .values(Collections.singletonList(unqualifiedWipReview.getString("ext_business_partner_code")))
                    .build());
            keywordCombRestriction.setRestrictions(restrictions);
            combRestrictions.add(keywordCombRestriction);
            //查询条件
            Where where = Where.builder()
                    .combRestrictions(combRestrictions)
                    .order(Order.builder()
                            .sortFields(Collections.singletonList(
                                    SortField.builder()
                                            .bmfClassName("businessPartner")
                                            .bmfAttributeName(BmfAttributeConst.ID)
                                            .fieldSort(FieldSort.DESC)
                                            .build()
                            )).build())
                    .build();
            result = this.bmfService.findPage("businessPartner", where, pageable);
        }
        return result;
    }
}
