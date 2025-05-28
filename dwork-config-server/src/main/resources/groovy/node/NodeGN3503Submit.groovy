package groovy.node

import cn.hutool.core.collection.CollectionUtil
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.service.BmfService
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.bmf.sql.Where
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.chinajey.application.common.holder.ThreadLocalHolder
import com.chinajey.application.common.utils.ValueUtil
import com.chinajey.application.script.engine.GroovyExecutor
import com.chinajey.dwork.common.utils.DomainAppCreate
import com.tengnat.dwork.common.constant.BmfClassNameConst
import com.tengnat.dwork.common.enums.TyDocStatusEnum
import com.tengnat.dwork.common.utils.CodeGenerator
import com.tengnat.dwork.modules.manufacture.service.PassBoxRealService
import com.tengnat.dwork.modules.manufacture.service.UnqualifiedWipReviewV2Service
import com.tengnat.dwork.modules.quality.service.AbnormalMaterialService
import com.tengnat.dwork.modules.script.abstracts.NodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

/**
 * 从迈金搬过来的
 *
 * 不合格评审提交脚本
 */
class NodeGN3503Submit extends NodeGroovyClass {

    protected Logger log = LoggerFactory.getLogger(getClass());
    AbnormalMaterialService abnormalMaterialService = SpringUtils.getBean("abnormalMaterialService");
    BasicGroovyService basicGroovyService = SpringUtils.getBean("basicGroovyService")
    SceneGroovyService sceneGroovyService = SpringUtils.getBean("sceneGroovyService")
    BmfService bmfService = SpringUtils.getBean("bmfService")
    UnqualifiedWipReviewV2Service unqualifiedWipReviewV2Service = SpringUtils.getBean("unqualifiedWipReviewV2Service")
    GroovyExecutor groovyExecutor = SpringUtils.getBean(GroovyExecutor.class)
    PassBoxRealService passBoxRealService = SpringUtils.getBean(PassBoxRealService.class)
    CodeGenerator codeGenerator = SpringUtils.getBean(CodeGenerator.class)

    private BmfObject material

    @Override
    BmfObject runScript(BmfObject nodeData) {
        log.info("-----------------------不合格评审提交脚本--开始--------------------------")

        List<BmfObject> passBoxes = nodeData.getAndRefreshList("passBoxes")
        if (CollectionUtils.isEmpty(passBoxes)) {
            throw new BusinessException("请选择周转箱")
        }
        String materialCode = nodeData.getString("ext_material_code")
        String code = nodeData.getString("ext_code")
        BmfObject unqualifiedWipReview = basicGroovyService.getByCode("unqualifiedWipReview", code)
        if (unqualifiedWipReview == null) {
            throw new BusinessException("不合格评审单不存在,编码:" + code)
        }
        String businessPartnerCode = nodeData.getString("ext_business_partner_code")
        String businessPartnerName = nodeData.getString("ext_business_partner_name")
        String sourceType = nodeData.getString("ext_source_type")
        String sourceCode = nodeData.getString("ext_source_code")

        //组装数据
        JSONObject jsonObject = new JSONObject();
        List<JSONObject> unqList = new ArrayList<>();
        jsonObject.put("code", code);
        jsonObject.put("sourceType", nodeData.getString("ext_source_type"));
        jsonObject.put("sourceCode", nodeData.getString("ext_source_code"));
        jsonObject.put("materialCode", nodeData.getString("ext_material_code"));
        jsonObject.put("materialName", nodeData.getString("ext_material_name"));
        jsonObject.put("reviewerCode", nodeData.getString("receiveObjectCode"));
        jsonObject.put("reviewerName", nodeData.getString("receiveObjectName"));
        jsonObject.put("participantsCode", nodeData.getString("sendObjectCode"));
        jsonObject.put("participantsName", nodeData.getString("sendObjectName"));

        for (BmfObject passBox : passBoxes) {
            if (sourceCode != passBox.getString("sourceOrderCode") && sourceType != passBox.getString("sourceOrderType")) {
                throw new BusinessException("周转箱来源类型或来源单据与不合格评审任务不一致")
            }
            passBox.putUncheck("passBoxRealCode", passBox.getString("code"))
            //判断结果
            String judgementResult = passBox.getString("ext_judgement_result")
            if (StringUtils.isBlank(judgementResult)) {
                throw new BusinessException("判定结果不能为空")
            } else {
                passBox.putUncheck("judgementResult", judgementResult)
            }
            //建议决策
            String decisionCode = passBox.getString("ext_specific_code")
            if (StringUtils.isBlank(decisionCode)) {
                throw new BusinessException("建议决策不能为空")
            } else {
                passBox.putUncheck("specificCode", decisionCode)
                passBox.putUncheck("specificName", passBox.getString("ext_specific_name"))
            }
            //评审备注
            passBox.putUncheck("reviewRemark", passBox.getString("ext_remark"))
            //责任分类
            String responsibilityClassification = passBox.getString("ext_responsibility_classification")
            if ("disQualified" == judgementResult) {
                //不良原因
                if (StringUtils.isBlank(passBox.getString("ext_defect_cause_code"))) {
                    throw new BusinessException("不良原因不能为空")
                }
                if (StringUtils.isBlank(responsibilityClassification)) {
                    throw new BusinessException("责任分类不能为空")
                }
                //责任部门   ext_responsible_dept_code   ext_responsible_dept_name
                if (StringUtils.isBlank(passBox.getString("ext_responsible_dept_code")) && "internal" == responsibilityClassification) {
                    throw new BusinessException("责任部门不能为空")
                }
                List<String> decisionLists = Arrays.asList("decision005", "decision011");
                if (decisionLists.contains(decisionCode) && StringUtils.isBlank(passBox.getString("ext_responsible_user_code"))) {
                    throw new BusinessException("责任人不能为空")
                }
                if (StringUtils.isBlank(passBox.getString("ext_liability_cause"))) {
                    throw new BusinessException("不良分类不能为空")
                }
            }
            def defectCause = this.basicGroovyService.getByCode("defectCause", passBox.getString("ext_defect_cause_code"))
            passBox.putUncheck("defectCause", defectCause)
            passBox.putUncheck("defectCauseCode", passBox.getString("ext_defect_cause_code"))
            passBox.putUncheck("defectCauseName", passBox.getString("ext_defect_cause"))
            passBox.putUncheck("defectCauseGroupCode", passBox.getString("ext_defect_cause_group_code"))
            passBox.putUncheck("defectCauseGroupName", passBox.getString("ext_defect_cause_group"))
            passBox.putUncheck("responsibilityClassification", responsibilityClassification)
            if ("external" == responsibilityClassification) {
                passBox.putUncheck("responsibleDeptCode", businessPartnerCode)
                passBox.putUncheck("responsibleDeptName", businessPartnerName)
            } else {
                passBox.putUncheck("responsibleDeptCode", passBox.getString("ext_responsible_dept_code"))
                passBox.putUncheck("responsibleDeptName", passBox.getString("ext_responsible_dept_name"))
            }


            passBox.putUncheck("responsibleUserCode", passBox.getString("ext_responsible_user_code"))
            passBox.putUncheck("responsibleUserName", passBox.getString("ext_responsible_user"))
            passBox.putUncheck("liabilityCause", passBox.getString("ext_liability_cause"))
            passBox.putUncheck("reworkProcess", passBox.getString("ext_rework_process"))
            unqList.add((JSONObject) JSONObject.toJSON(passBox))
        }

        //现场不合格评审提交
        jsonObject.put("passBoxes", unqList);
        BmfObject submit = unqualifiedWipReviewV2Service.sceneSubmit(jsonObject)

        //反写OA数据 ext_buExamine 是否BU组通知
        submit.putUncheck("ext_buExamine", nodeData.get("ext_buExamine") == null ? false : nodeData.getBoolean("ext_buExamine"))
        createOa(submit)
        BmfObject inspectionType = unqualifiedWipReview.getAndRefreshBmfObject("inspectionType")
        String inspectionTypeCode = inspectionType.getString("code")
        //创建挑选任务
        //不合格评审单明细
        List<BmfObject> unqualifiedWipReviewDetails = unqualifiedWipReview.getAndRefreshList("unqualifiedWipReviewDetails");
        List<BmfObject> decision020 = new ArrayList<>()//挑选
        for (BmfObject unqualifiedWipReviewDetail : unqualifiedWipReviewDetails) {
            String specificCode = unqualifiedWipReviewDetail.getString("specificCode")
            if ("decision020".equals(specificCode)) {
                decision020.add(unqualifiedWipReviewDetail)
            }
        }
        BmfObject inspectionSheet = bmfService.findByUnique(BmfClassNameConst.INSPECTION_SHEET, "code", nodeData.getString("ext_inspection_sheet_code"))
        if (inspectionSheet != null) {
            inspectionSheet.put("unqualifiedReviewCode", code)
            createSelectByPassBox(decision020, inspectionSheet)
        }

        //修改评审状态
        nodeData.put("ext_status", submit.getString("status"))

        unlockPassBoxes(passBoxes, jsonObject.getString("sourceType"))

//        if ("LX00005".equals(inspectionTypeCode)) {//完工入库检
//            material = basicGroovyService.findOne("material", "code", materialCode)
//            if (material == null) {
//                throw new BusinessException("待评审物料信息不存在")
//            }
//            //如果是让步接收的话，生成搬运入库任务
//            createCarryTask(nodeData, passBoxes)
//        }


        log.info("-----------------------不合格评审提交脚本--结束----------------------------")
        return nodeData
    }

    //如果是让步接收的话，解锁周转箱
    private void unlockPassBoxes(List<BmfObject> passBoxes, String sourceType) {
        List<BmfObject> realPassBoxes = passBoxes.stream().filter(item -> StringUtils.equals(item.getString("ext_specific_code"), "decision017"))
                .map(passBox -> bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, "code", passBox.getString("passBoxRealCode")))
                .collect(Collectors.toList())
        if (CollectionUtil.isNotEmpty(realPassBoxes)) {
            passBoxRealService.unLockForReal(realPassBoxes)
            if ("purchaseReceipt".equals(sourceType)) {
                //让步接受并且来源是采购收货：周转箱改为合格，并且创建采购入库合格任务
                List<BmfObject> updateList = realPassBoxes.stream().map(item -> {
                    BmfObject updateOne = BmfObject.reNewUpdateObj(item)
                    updateOne.put("loadMaterialStatusType", "qualified")
                    return updateOne
                }).collect(Collectors.toList())
                bmfService.updateByPrimaryKeySelective(updateList)
            }
        }

    }


    void createOa(BmfObject unqualifiedWipReview) {
        //是否OA审批
        boolean isOA = false;
        //状态
        String status = unqualifiedWipReview.getString("status");
        if ("reviewed" == status) {
            if (isOA) {

            } else {
                //检验单
                String inspectionSchemeCode = null;
                BmfObject inspectionSheet = basicGroovyService.findOne(BmfClassNameConst.INSPECTION_SHEET, "code", unqualifiedWipReview.getString("inspectionSheetCode"));
                if (inspectionSheet != null) {
                    inspectionSchemeCode = inspectionSheet.getString("inspectionSchemeCode");
                }
                List<BmfObject> unqualifiedWipReviewDetails = unqualifiedWipReview.getList("unqualifiedWipReviewDetails");
                for (BmfObject unqualifiedWipReviewDetail : unqualifiedWipReviewDetails) {
                    unqualifiedWipReviewDetail.put("approvalStatus", "pass");
                    unqualifiedWipReviewDetail.put("realitySpecificCode", unqualifiedWipReviewDetail.getString("specificCode"));
                    unqualifiedWipReviewDetail.put("realitySpecificName", unqualifiedWipReviewDetail.getString("specificName"));
                    basicGroovyService.saveOrUpdate(unqualifiedWipReviewDetail);
                }
                //创建异常物料
                List<BmfObject> abnormalMaterialList = abnormalMaterialService.createAbnormalMaterial(unqualifiedWipReview.getString("code"), inspectionSchemeCode, "");
                createPurchaseReceipt(unqualifiedWipReview)
                //异常物料自动处理
                autoProcessAbnormalMaterial(abnormalMaterialList)
            }
        }
    }

    /**
     * 自动处理异常物料
     * @param unqualifiedWipReview
     */
    void autoProcessAbnormalMaterial(List<BmfObject> abnormalMaterialList) {
        //除返工工单其他都自动处理
        List<BmfObject> tempAbnormalMaterialList = abnormalMaterialList.stream().filter(item -> !StringUtils.equals("decision009", item.getString("specificCode"))).collect(Collectors.toList())
        JSONObject data = new JSONObject()
        JSONObject param = new JSONObject()
        param.put("abnormal", tempAbnormalMaterialList)
        data.put("data", param)
        groovyExecutor.execute("AopAfterAbnormalDecisionProcess", data)
        tempAbnormalMaterialList.forEach(abnormalMaterial -> {
            //调整处理状态
            abnormalMaterial.put("status", "processing")
        })
        for (BmfObject tempAbnormalMaterial : tempAbnormalMaterialList) {
            basicGroovyService.updateByPrimaryKeySelective(tempAbnormalMaterial)
        }
    }

    /**
     * 创建采购入库单
     * @param unqualifiedWipReview
     */
    void createPurchaseReceipt(BmfObject unqualifiedWipReview) {
        //获取对应检验单 与报检nodeDate
        if (!StringUtils.equals("purchaseOrderTask", unqualifiedWipReview.getString("sourceType"))) {
            return
        }
        BmfObject purchaseOrderTask = basicGroovyService.getByCode(unqualifiedWipReview.getString("sourceType"), unqualifiedWipReview.getString("sourceCode"))
        BmfObject inspectionSheet = basicGroovyService.getByCode("inspectionSheet", unqualifiedWipReview.getString("inspectionSheetCode"))
        if (inspectionSheet == null || purchaseOrderTask == null) {
            throw new BusinessException("数据错误:检验单或者来源单据不存在!")
        }
        Long instanceNodeId = inspectionSheet.getLong("instanceNodeId")
        Long logisticsAppId = inspectionSheet.getLong("logisticsAppId")

        if (instanceNodeId == null && logisticsAppId == null) {
            throw new BusinessException("节点数据异常")
        }
        BmfObject buzSceneInstanceNode = basicGroovyService.findOne("buzSceneInstanceNode", instanceNodeId)

        if (buzSceneInstanceNode == null && buzSceneInstanceNode == null) {
            throw new BusinessException("节点数据异常")
        }
        BmfObject node = basicGroovyService.findOne(buzSceneInstanceNode.getString("appCode"), logisticsAppId)

        JSONArray array = unqualifiedWipReview.getJSONArray("unqualifiedWipReviewDetails")
        if (CollectionUtil.isEmpty(array)) {
            throw new BusinessException("数据错误:未找到明细信息!")
        }
        List<JSONObject> passBoxs = new ArrayList<>()
        for (int i = 0; i < array.size(); i++) {
            passBoxs.add(array.getJSONObject(i))
        }
        //周转箱托盘有不同的周转箱入采购入库任务 就把周转箱的托盘都拆开
        //根据托盘分组
        Map<String, List<BmfObject>> mapList = passBoxs.stream()
        //复检跳过
                .filter(obj -> !StringUtils.equals("decision006", obj.getString("specificCode")))
                .collect(Collectors.groupingBy(obj -> ValueUtil.toStr(obj.getString("palletCode"))))
        //入合格的决策  合格,让步接收,客户特采,供应商特采
        List<String> hgList = Arrays.asList("decision010", "decision001", "decision003", "decision016")
        //入不合格的决策 退货  报废(不可回炉)
        List<String> bhgList = Arrays.asList("decision004", "decision005")
        //处理完的数据  一个托盘就一个hgList
        Map<String, List<BmfObject>> handleMap = new HashMap<>();
        //处理后数据
        List<BmfObject> handleList = new ArrayList<>();

        //判断同托周转箱是否处理决策一致
        for (String key : mapList.keySet()) {
            if (StringUtils.isNotBlank(key)) {
                List<BmfObject> mapPassBoxes = mapList.get(key)
                //用于判断是否一致
                String palletName = mapPassBoxes.get(0).getString("palletName")
                Boolean flag = false
                List<String> decision = new ArrayList<>();

                for (BmfObject passBox : mapPassBoxes) {
                    if (CollectionUtil.isEmpty(decision)) {
                        if (hgList.contains(passBox.getString("specificCode"))) {
                            decision = hgList
                        } else {
                            decision = bhgList
                        }
                    } else {
                        //不一致,拆托
                        if (!decision.contains(passBox.getString("specificCode"))) {
                            flag = true
                            break
                        }
                    }
                }
                List<BmfObject> passBoxReals = mapPassBoxes.stream()
                        .map(passBox -> {
                            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("passBoxRealCode"))
                            if (passBoxReal != null) {
                                passBoxReal.putUncheck("mainDate", null)
                                passBoxReal.putUncheck("submit", null)
                                if (StringUtils.isNotBlank(key)) {
                                    passBoxReal.put("thisPallet", true)
                                }
                                passBoxReal.putUncheck("palletCode", key)
                                passBoxReal.putUncheck("palletName", palletName)
                                passBoxReal.putUncheck("specificCode", passBox.getString("specificCode"))
                            }
                            return passBoxReal
                        })
                        .filter(passBoxReal -> passBoxReal != null)
                        .collect(Collectors.toList())
                //判断数据
                if (CollectionUtil.isNotEmpty(passBoxReals)) {
                    if (flag) {
                        sceneGroovyService.tearPallet(passBoxReals)
                        for (BmfObject passBoxReal : passBoxReals) {
                            passBoxReal.putUncheck("palletCode", "")
                            passBoxReal.putUncheck("palletName", "")
                            passBoxReal.put("thisPallet", false)
                        }
                    }
                    handleList.addAll(passBoxReals)
                }
            } else {
                List<BmfObject> mapPassBoxes = mapList.get(key)
                List<BmfObject> passBoxReals = mapPassBoxes.stream()
                        .map(passBox -> {
                            BmfObject passBoxReal = basicGroovyService.getByCode("passBoxReal", passBox.getString("passBoxRealCode"))
                            if (passBoxReal != null) {
                                passBoxReal.putUncheck("palletCode", "")
                                passBoxReal.putUncheck("palletName", "")
                                passBoxReal.putUncheck("specificCode", passBox.getString("specificCode"))
                            }
                            return passBoxReal
                        })
                        .filter(passBoxReal -> passBoxReal != null)
                        .collect(Collectors.toList())
                handleList.addAll(passBoxReals)
            }
        }
        //处理后的数据Map
        Map<String, List<BmfObject>> map = handleList.stream().collect(Collectors.groupingBy(obj -> ValueUtil.toStr(obj.getString("palletCode"))))
        //对应的周转箱
        passBoxs.stream()
                .filter(obj -> !StringUtils.equals("decision006", obj.getString("specificCode")) && !StringUtils.equals("decision004", obj.getString("specificCode")))
                .forEach(passBox -> {
                    //除了退货其他的异常物料都已处理
                    List<BmfObject> abnormalMaterials = basicGroovyService.find("abnormalMaterial", Where.builder()
                            .restrictions(Arrays.asList(
                                    Restriction.builder()
                                            .conjunction(Conjunction.AND)
                                            .operationType(OperationType.EQUAL)
                                            .attributeName("passBoxRealCode")
                                            .values(Collections.singletonList(passBox.getString("passBoxRealCode")))
                                            .build()
                                    ,
                                    Restriction.builder()
                                            .conjunction(Conjunction.AND)
                                            .operationType(OperationType.EQUAL)
                                            .attributeName("reviewCode")
                                            .values(Collections.singletonList(unqualifiedWipReview.getString("code")))
                                            .build()
                            )).build())

                    //已处理
                    if (CollectionUtil.isNotEmpty(abnormalMaterials)) {
                        for (BmfObject abnormalMaterial : abnormalMaterials) {
                            BmfObject update = new BmfObject(abnormalMaterial.getBmfClassName())
                            update.put("status", "processed")
                            update.put("id", abnormalMaterial.getPrimaryKeyValue())
                            basicGroovyService.updateByPrimaryKeySelective(update)
                        }
                    }
                })

        for (String key : map.keySet()) {
            //有托
            String gnBmfClass = "GN3055"
            if (StringUtils.isNotBlank(key)) {
                List<BmfObject> passBoxes = map.get(key)

                String specificCode = passBoxes.get(0).getString("specificCode")
                if (hgList.contains(specificCode)) {
                    gnBmfClass = "GN3054"
                } else {
                    gnBmfClass = "GN3055"
                }
                newTaskByPallet(purchaseOrderTask, passBoxes, gnBmfClass, node)

            } else {
                //无托 拆分合格与不合格
                List<BmfObject> passBoxes = map.get(key)
                Map<String, List<BmfObject>> groupingBy = passBoxes.stream()
                        .collect(Collectors.groupingBy(obj -> hgList.contains(obj.getString("specificCode")) ? "hg" : "noHg"))
                for (String key1 : groupingBy.keySet()) {
                    List<BmfObject> hgPassBoxes = groupingBy.get(key1)
                    if (StringUtils.equals("hg", key1)) {
                        gnBmfClass = "GN3054"
                    } else {
                        gnBmfClass = "GN3055"
                    }
                    newTaskByPallet(purchaseOrderTask, hgPassBoxes, gnBmfClass, node)
                }
            }
        }
    }

    /**
     * 按托创建任务
     *
     * @param purchaseOrderTask 采购收货任务
     * @param passBoxList 周转箱集合
     * @param gnBmfClass gnBmfClass
     * @param node node节点
     */
    void newTaskByPallet(BmfObject purchaseOrderTask, List<BmfObject> passBoxList, String gnBmfClass, BmfObject node) {
        //检验单
        String providerCode = purchaseOrderTask.getString("providerCode")
        String warehouseCode = purchaseOrderTask.getString("warehouseCode")
        String materialCode = purchaseOrderTask.getString("materialCode")
        String internalVersionNumber = purchaseOrderTask.getString("internalVersionNumber")
        //提交数量
        BigDecimal sum = passBoxList.stream()
                .map(box -> box.getBigDecimal("quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add)

        passBoxList
        //查询采购入库 存在相同任务合并任务 不存在新增任务
        String sql = String.format("SELECT\n" +
                "\tt0.id \n" +
                "FROM\n" +
                "\tdwk_logistics_custom_%s AS t0\n" +
                "\tLEFT JOIN dwk_logistics_custom_%s_ext AS t1 ON t0.id = t1.ext_%s_id\n" +
                "\tLEFT JOIN dwk_logistics_custom_%s_passboxes AS t2 ON t0.id = t2.main_id \n" +
                "WHERE\n" +
                "\tt0.is_delete = '0' \n" +
                "\tAND t1.is_delete = '0' \n" +
                "\tAND t0.logistics_status IN ('0', '1', '2')\n" +
                "\tAND t1.ext_partener_code = '%s' \n" +
                "\tAND t1.ext_warehouse_code = '%s' \n" +
                "\tAND t1.ext_material_code = '%s' \n" +
                "\tAND t1.ext_internal_version_number = '%s' \n" +
                "\tAND t2.pallet_code = '%s' \n" +
                "ORDER BY\n" +
                "\tt2.create_time \n" +
                "\tLIMIT 1", gnBmfClass, gnBmfClass, gnBmfClass, gnBmfClass, providerCode, warehouseCode, materialCode, internalVersionNumber, passBoxList.get(0).getString("palletCode"))
        Map<String, Object> map = basicGroovyService.findOne(sql)
        if (map != null && map.size() > 0) {
            //合任务
            Long id = Long.parseLong(map.get("id").toString())
            BmfObject gn = basicGroovyService.findOne(gnBmfClass, id)
            def gnPassBox = gn.getAndRefreshList("passBoxes")
            def gnTasks = gn.getAndRefreshList("tasks")

            passBoxList.forEach(item -> {
                item.remove("id")
                item.putUncheck("mainDate", null)
                item.putUncheck("submit", null)
                BmfObject gn1 = BmfUtils.genericFromJsonExt(item, gnBmfClass + "PassBoxes")
                BmfObject gn2 = BmfUtils.genericFromJsonExt(item, gnBmfClass + "Tasks")
                gnPassBox.add(gn1)
                gnTasks.add(gn2)
            })
            gn.put("passBoxes", gnPassBox)
            gn.put("tasks", gnTasks)
            gn.put("ext_notinwarehouseqty", gn.getBigDecimal("ext_notinwarehouseqty").add(sum))
            basicGroovyService.saveOrUpdate(gn)
            //更新周转箱实时表下个节点任务
            sceneGroovyService.updateNextOperateSourceId(passBoxList, gn)
        } else {
            //生产新任务
            BmfObject newTask = node.deepClone()
            newTask.put("sendObjectCode", null)
            newTask.put("sendObjectName", null)
            newTask.put("id", null)
            newTask.put("ext_partener_code", purchaseOrderTask.getString("providerCode"))
            newTask.put("ext_partener_name", purchaseOrderTask.getString("providerName"))
            newTask.put("ext_material_code", purchaseOrderTask.getString("materialCode"))
            newTask.put("ext_material_name", purchaseOrderTask.getString("materialName"))
            newTask.put("ext_specification", purchaseOrderTask.getString("specification"))
            newTask.put("ext_internal_version_number", purchaseOrderTask.getString("internalVersionNumber"))
            newTask.put("ext_warehouse_code", purchaseOrderTask.getString("warehouseCode"))
            newTask.put("ext_warehouse_name", purchaseOrderTask.getString("warehouseName"))
            newTask.put("ext_notinwarehouseqty", sum)
            newTask.put("ext_product_code", purchaseOrderTask.getString("productCode"))
            newTask.put("targetLocationCode", null)
            newTask.put("targetLocationName", null)
            List<BmfObject> gnPassBox = new ArrayList<>()
            List<BmfObject> gnTasks = new ArrayList<>()
            Set<String> unTask = new HashSet<>()
            passBoxList.forEach(item -> {
                item.remove("id")
                item.putUncheck("mainDate", null)
                item.putUncheck("submit", null)
                BmfObject gn1 = BmfUtils.genericFromJsonExt(item, gnBmfClass + "PassBoxes")
                gnPassBox.add(gn1)
                BmfObject gn2 = BmfUtils.genericFromJsonExt(item, gnBmfClass + "Tasks")
                String unKey = item.getString("materialCode") + item.getString("palletCode")
                if (!unTask.contains(unKey)) {
                    unTask.add(unKey)
                    gnTasks.add(gn2)
                }
            })
            newTask.put("passBoxes", gnPassBox)
            newTask.put("tasks", gnTasks)

            if ("GN3054".equals(gnBmfClass)) {
                //合格
                sceneGroovyService.dataFlow(newTask)
            } else {
                newTask.put("buzSceneInstanceNode", null)
                sceneGroovyService.buzSceneStart("GN3055", "BS3016", newTask)
            }
        }
    }

    private void createSelectByPassBox(List<BmfObject> passBoxes, BmfObject inspectionSheet) {
        //不合格评审单
        BmfObject unqualifiedWipReview = bmfService.findByUnique(BmfClassNameConst.UNQUALIFIED_WIP_REVIEW, "code", inspectionSheet.getString("unqualifiedReviewCode"))
        for (BmfObject passBox : passBoxes) {
            BmfObject pick = new BmfObject(BmfClassNameConst.PICK);
            pick = this.codeGenerator.setCode(pick);
            pick.put("sourceType", inspectionSheet.get("sourceType"));
            pick.put("sourceCode", inspectionSheet.get("sourceCode"));
            pick.put("inspectionSheet", inspectionSheet);
            pick.put("sheetCode", inspectionSheet.get("code"));
            pick.put("unqualifiedWipReview", unqualifiedWipReview);
            pick.put("unqualifiedReviewCode", unqualifiedWipReview.get("code"));
            pick.put("responsibilityClassification", passBox.get("responsibilityClassification"));
            BmfObject inspectionType = inspectionSheet.getAndRefreshBmfObject("inspectionType");
            pick.put("inspectionType", inspectionType);
            pick.put("inspectionTypeName", inspectionType.get("name"));
            pick.put("passBoxCode", passBox.get("passBoxCode"));
            pick.put("passBoxName", passBox.get("passBoxName"));
            pick.put("passBoxRealCode", passBox.get("passBoxRealCode"));
            pick.put("materialCode", unqualifiedWipReview.get("materialCode"));
            pick.put("materialName", unqualifiedWipReview.get("materialName"));
            pick.put("quantity", passBox.get("quantity"));
            BmfObject passBoxReal = bmfService.findByUnique("passBoxReal", "code", pick.getString("passBoxRealCode"));
            pick.put("locationCode", passBoxReal.getString("locationCode"));
            pick.put("locationName", passBoxReal.getString("locationName"));
            BmfObject unit = passBox.getAndRefreshBmfObject("unit");
            String unitName = passBox.getString("unitName")
            if (unit != null && !unitName.isEmpty()) {
                unit.put("name", unitName);
            }
            pick.put("quantityUnitName", unitName);
            pick.put("quantityUnit", passBox.get("unit"));
            pick.put("sendResourceCode", ThreadLocalHolder.getLoginInfo().getResource().getResourceCode());
            pick.put("sendResourceName", ThreadLocalHolder.getLoginInfo().getResource().getResourceName());
            pick.put("sendResourceTime", new Date());
            pick.put("status", TyDocStatusEnum.CREATE.getCode());
            bmfService.saveOrUpdate(pick);
        }
    }

//    //让步接收的生成搬运入库任务
//    private void createCarryTask(BmfObject nodeData, List<BmfObject> passBoxes) {
//        if (!StringUtils.equals(nodeData.getString("ext_source_type"), "production")) {
//            //非完工入库检验类型
//            return
//        }
//        List<BmfObject> passBoxReals = passBoxes.stream().filter(item -> StringUtils.equals(item.getString("ext_specific_code"), "decision017"))
//                .map(passBox -> {
//                    BmfObject realPassBox = bmfService.findByUnique(BmfClassNameConst.PASS_BOX_REAL, "passBoxCode", passBox.getString("passBoxCode"))
//                    realPassBox.put("serialNumbers", passBox.getString("serialNumbers"))
//                    return realPassBox
//                })
//                .collect(Collectors.toList())
//        if (CollectionUtil.isNotEmpty(passBoxReals)) {
//            BmfObject inspectionSheet = bmfService.findByUnique(BmfClassNameConst.INSPECTION_SHEET, "code", nodeData.getString("ext_inspection_sheet_code"))
//            if (inspectionSheet == null) throw new BusinessException("检验单信息不存在")
//            String boxOrderCode = inspectionSheet.getString("boxOrderCode")
//            if (StringUtils.isEmpty(boxOrderCode)) throw new BusinessException("箱单编码不存在")
//            BmfObject boxOrder = basicGroovyService.getByCode(com.chinajey.dwork.company.common.constant.BmfClassNameConst.BOX_ORDER, boxOrderCode)
//            if (boxOrder == null) throw new BusinessException("箱单编码${boxOrderCode}对应的箱单信息不存在")
//            BmfObject productOrder = basicGroovyService.getByCode(BmfClassNameConst.PRODUCT_ORDER, boxOrder.getString("productOrderCode"))
//            if (productOrder == null) throw new BusinessException("生成订单信息不存在")
//
//            BigDecimal quantity = passBoxReals.sum {
//                it.getBigDecimal("quantity")
//            } as BigDecimal
//            if (quantity <= BigDecimal.ZERO) return
//            String locationCode = passBoxReals.first().getString("locationCode")
//            String locationName = passBoxReals.first().getString("locationName")
//            //构造任务表
//            JSONObject task = new JSONObject()
//            task.put("materialCode", material.getString("code"))
//            task.put("materialName", material.getString("name"))
//            task.put("material", material)
//            task.put("quantityUnit", material.getAndRefreshBmfObject("flowUnit"))
//
//            //构造custom信息
//            JSONObject custom = new JSONObject()
//            custom.put("dataSourceCode", productOrder.getString("code"))
//            custom.put("dataSourceType", productOrder.getBmfClassName())
//            custom.put("ext_material_code", material.getString("code")) // 物料编码
//            custom.put("ext_material_name", material.getString("name")) // 物料名称
//            custom.put("ext_current_location_code", locationCode) // 当前位置编码
//            custom.put("ext_current_location_name", locationName) // 当前位置名称
//            custom.put("sourceLocationName", locationCode) // 当前位置名称
//            custom.put("sourceLocationCode", locationName) // 当前位置名称
//            custom.put("ext_quantity", quantity) // 数量
//            custom.put("ext_target_warehouse_code", productOrder.getString("inboundWarehouseCode") ?: material.getString("defaultWarehouseCode"))
//            // 目标仓库编码
//            custom.put("ext_target_warehouse_name", productOrder.getString("inboundWarehouseName") ?: material.getString("defaultWarehouseName"))
//            // 目标仓库名称
//            custom.put("ext_box_order_code", boxOrder.getString("code")) // 箱单编码
//            custom.put("ext_product_order_code", boxOrder.getString("productOrderCode")) // 生成订单编码
//            new DomainAppCreate().createGN0012Task(custom, Arrays.asList(task), passBoxReals)
//        }
//
//    }
}
