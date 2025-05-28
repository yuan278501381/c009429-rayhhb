package com.chinajey.dwork.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.chinajay.virgo.bmf.obj.BmfObject;
import com.chinajay.virgo.bmf.sql.Conjunction;
import com.chinajay.virgo.bmf.sql.OperationType;
import com.chinajay.virgo.bmf.sql.Restriction;
import com.chinajay.virgo.bmf.sql.Where;
import com.chinajay.virgo.utils.SpringUtils;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.domain.DomainWarehouse;
import com.tengnat.dwork.common.utils.BigDecimalUtils;
import com.tengnat.dwork.modules.script.service.BasicGroovyService;
import com.tengnat.dwork.modules.script.service.SceneGroovyService;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 构建移动应用
 */
public class DomainAppCreate {

    private final SceneGroovyService sceneGroovyService;
    private final BasicGroovyService basicGroovyService;

    public DomainAppCreate() {
        this.sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class);
        this.basicGroovyService = SpringUtils.getBean(BasicGroovyService.class);
    }

    /**
     * 生成移动应用任务
     */
    public void createAPPTask(String bmfClass, JSONObject custom, List<JSONObject> tasks, List<JSONObject> passBoxes) {
        BmfObject appGN = new BmfObject(bmfClass);
        custom.remove("id");
        appGN.putAll(custom);
        appGN.put("passBoxes", createGnPassBoxes(bmfClass, passBoxes));
        appGN.put("tasks", createGnTasks(bmfClass, tasks));
        sceneGroovyService.buzSceneStart(bmfClass, appGN);
    }

    /**
     * 生成移动应用任务(任务节点不是首个节点)
     */
    public void createAPPTaskV2(String bmfClass, JSONObject custom, List<JSONObject> tasks, List<JSONObject> passBoxes) {
        BmfObject appGN = new BmfObject(bmfClass);
        custom.remove("id");
        appGN.putAll(custom);
        appGN.put("passBoxes", createGnPassBoxes(bmfClass, passBoxes));
        appGN.put("tasks", createGnTasks(bmfClass, tasks));
        basicGroovyService.saveOrUpdate(appGN);
    }

    public List<BmfObject> createGnTasks(String bmfClass, List<JSONObject> tasks) {
        if (CollectionUtil.isNotEmpty(tasks)) {
            return tasks.stream().map(it -> {
                BmfObject appTask = new BmfObject(bmfClass + "Tasks");
                appTask.putAll(it);
                appTask.remove("id");
                return appTask;
            }).collect(Collectors.toList());
        }
        return null;
    }

    private List<BmfObject> createGnPassBoxes(String bmfClass, List<JSONObject> passBoxes) {
        if (CollectionUtil.isNotEmpty(passBoxes)) {
            return passBoxes.stream().map(it -> {
                BmfObject appPassBoxes = new BmfObject(bmfClass + "PassBoxes");
                appPassBoxes.putAll(it);
                appPassBoxes.remove("id");
                appPassBoxes.put("submit", false);
                return appPassBoxes;
            }).collect(Collectors.toList());
        }
        return null;
    }

    /**
     * 生成搬运入库任务
     * 如果存在同生成订单，同物料，同目标仓库的，进行合并
     */
    public void createGN0012Task(JSONObject custom, List<JSONObject> tasks, List<JSONObject> passBoxes) {
        //判断仓库是否是中转仓，如果是中转仓，不生成搬运入库任务
        if (new DomainWarehouse().isLineWarehouse(custom.getString("ext_target_warehouse_code"))) {
            return;
        }
        String bmfClass = "GN0012";
        String productOrderCode = custom.getString("ext_product_order_code"); //生成订单编码
        String materialCode = custom.getString("ext_material_code"); //物料编码
        String warehouseCode = custom.getString("ext_target_warehouse_code"); //目标仓库编码
        if (StringUtils.isEmpty(productOrderCode)) throw new BusinessException("缺少生成订单信息");
        if (StringUtils.isEmpty(materialCode)) throw new BusinessException("缺少物料信息");
        if (StringUtils.isEmpty(warehouseCode)) throw new BusinessException("缺少目标仓库信息");
        BmfObject gn0012Object = basicGroovyService.findOne(bmfClass, Where.builder()
                .restrictions(Arrays.asList(
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("ext_product_order_code")
                                .values(Collections.singletonList(productOrderCode))
                                .build(),
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("ext_material_code")
                                .values(Collections.singletonList(materialCode))
                                .build(),
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.EQUAL)
                                .attributeName("ext_target_warehouse_code")
                                .values(Collections.singletonList(warehouseCode))
                                .build(),
                        Restriction.builder()
                                .conjunction(Conjunction.AND)
                                .operationType(OperationType.IN)
                                .attributeName("logisticsStatus")
                                .values(Arrays.asList("1", "2"))
                                .build()
                ))
                .build());
        if (gn0012Object != null) {
            //数量合并
            BigDecimal ext_quantity = gn0012Object.getBigDecimal("ext_quantity");
            gn0012Object.put("ext_quantity", BigDecimalUtils.add(custom.getBigDecimal("ext_quantity"), ext_quantity));
            if (StringUtils.isEmpty(gn0012Object.getString("sourceLocationCode"))) {
                gn0012Object.put("sourceLocationCode", custom.getString("sourceLocationCode"));
                gn0012Object.put("sourceLocationName", custom.getString("sourceLocationName"));
            }
            //合并序列号
            List<BmfObject> newPassBoxes = new ArrayList<>();
            if (CollectionUtil.isNotEmpty(passBoxes)) {
                List<BmfObject> gnPassBoxes = createGnPassBoxes(bmfClass, passBoxes);
                List<BmfObject> gn0012PassBoxes = gn0012Object.getAndRefreshList("passBoxes");
                if (gnPassBoxes != null && CollectionUtil.isNotEmpty(gnPassBoxes)) {
                    for (BmfObject passBox : gnPassBoxes) {
                        if (passBox == null) continue;
                        List<BmfObject> existBoxes = gn0012PassBoxes.stream().filter(it -> StringUtils.equals(it.getString("passBoxCode"), passBox.getString("passBoxCode"))).collect(Collectors.toList());
                        String serialNumbers = passBox.getString("serialNumbers");
                        if (!StringUtils.isEmpty(serialNumbers) && CollectionUtil.isNotEmpty(existBoxes)) {
                            BmfObject existBox = existBoxes.get(0);
                            String oldSerialNumbers = existBox.getString("serialNumbers");
                            StringBuilder oldSerialNumbersBuild = new StringBuilder();
                            if (StringUtils.isNotBlank(oldSerialNumbers)) {
                                oldSerialNumbersBuild.append(oldSerialNumbers);
                            }
                            //更新老的序列号
                            for (String serialNumber : serialNumbers.split(",")) {
                                if (!oldSerialNumbersBuild.toString().contains(serialNumber)) {
                                    if (!oldSerialNumbersBuild.toString().isEmpty()) {
                                        oldSerialNumbersBuild.append(",").append(serialNumber);
                                    } else {
                                        oldSerialNumbersBuild.append(serialNumber);
                                    }
                                }
                            }
                            existBox.put("serialNumbers", oldSerialNumbersBuild.toString());
                            newPassBoxes.add(existBox);
                            continue;
                        }
                        newPassBoxes.add(passBox);
                    }
                }

                for (BmfObject oldPassBox : gn0012PassBoxes) {
                    boolean judgeExist = newPassBoxes.stream().anyMatch(it -> StringUtils.equals(it.getString("passBoxCode"), oldPassBox.getString("passBoxCode")));
                    if (!judgeExist) newPassBoxes.add(oldPassBox);
                }
                gn0012Object.put("passBoxes", newPassBoxes);
            }
            gn0012Object.getAndRefreshList("tasks");
            this.basicGroovyService.updateByPrimaryKeySelective(gn0012Object);
            this.basicGroovyService.saveOrUpdate(gn0012Object);

        } else {
            createAPPTask(bmfClass, custom, tasks, passBoxes);
        }
    }

}
