package com.chinajey.dwork.modules.standar_interface.process_route.form;

import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.ExtForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class ExternalProcessRouteForm extends CodeForm {

    @NotBlank(message = "工艺路线名称不能为空")
    private String name;

    @NotBlank(message = "产品编码不能为空")
    private String materialCode;

    @CodeDict(value = "processRouteType", message = "工艺路线类型值错误")
    private String type;

    private Boolean status = Boolean.TRUE;

    private Boolean isDefault = Boolean.FALSE;

    private String remark;

    @Valid
    @NotEmpty(message = "工艺路线明细不能为空")
    private List<Detail> details;

    private List<String> routeFiles;

    @Getter
    @Setter
    public static class Detail extends ExtForm {

        @NotNull(message = "工序号不能为空")
        private Integer processNo;

        @NotBlank(message = "工序编码不能为空")
        private String processCode;

        @NotNull(message = "工序系数不能为空")
        private BigDecimal coefficient;

        @NotNull(message = "损耗率不能为空")
        private BigDecimal lossRate;

        private BigDecimal standardCapacity;

        @NotBlank(message = "流转单位名称不能为空")
        private String flowUnitName;

        @NotNull(message = "单重不能为空")
        private BigDecimal weight;

        private String weightUnitName;

        @CodeDict(value = "deliveryTargetType", message = "物料配送目标类型值错误")
        private String deliveryTargetType;

        private String deliveryTargetCode;

        private String processingContent;

        @CodeDict(value = "WlOpType", message = "生产模式值错误")
        private String productionMode;

        @CodeDict(value = "capacityMode", message = "产能方式值错误")
        private String capacityMode;

        private String sceneCode;

        private Boolean isPack = Boolean.FALSE;

        private Boolean prepareMaterial = Boolean.TRUE;

        private Boolean isOutsource = Boolean.FALSE;

        @CodeDict(value = "wpTransType", message = "工序转运方式值错误")
        private String processTransferMode;

        private Integer circulationBatchQuantity;

        @CodeDict(value = "processComType", message = "组合类型值错误")
        private String comType;

        private String comName;

        private Boolean thisSerialNumber = Boolean.FALSE;

        private Boolean thisMainSerialNumber = Boolean.FALSE;

        /**
         * 相似工序
         */
        private List<String> similarProcesses;

        /**
         * 原材料信息
         */
        @Valid
        private List<Material> materials;

        /**
         * 可用资源
         */
        @Valid
        private List<Resource> resources;

        /**
         * 推荐人数
         */
        @Valid
        private List<Referral> referrals;

        /**
         * 副产品
         */
        private List<String> sideProducts;

        /**
         * 工艺资源
         */
        @Valid
        private List<ProcessResource> processResources;

        /**
         * 量产设置
         */
        @Valid
        private List<MassSetting> massSettings;

    }

    @Getter
    @Setter
    public static class Material {

        @NotNull(message = "原材料编码不能为空")
        private String materialCode;

        @NotNull(message = "原材料基本用量不能为空")
        private BigDecimal basicUsage;

        @NotNull(message = "原材料损耗率不能为空")
        private BigDecimal lossRate;

        private Boolean thisSerialNumber = Boolean.FALSE;

        private Boolean thisMainSerialNumber = Boolean.FALSE;
    }


    @Getter
    @Setter
    public static class Resource {

        @NotBlank(message = "可用资源类型不能为空")
        private String resourceType;

        @NotBlank(message = "可用资源编码不能为空")
        private String resourceCode;
    }

    @Getter
    @Setter
    public static class Referral {

        @NotNull(message = "最小计划数量不能为空")
        private Integer min;

        @NotNull(message = "最大计划数量不能为空")
        private Integer max;

        @NotNull(message = "推荐人数不能为空")
        private Integer referral;
    }

    @Getter
    @Setter
    public static class ProcessResource {

        @NotBlank(message = "工艺资源类型不能为空")
        @CodeDict(value = "processResourceType", message = "工艺资源类型值错误")
        private String type;

        @NotBlank(message = "工艺资源编码不能为空")
        private String resourceCode;

        /**
         * 单位：秒（S）
         */
        @NotNull(message = "标准工时不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "标准工时不能小于0")
        private BigDecimal workHour;

        private BigDecimal adjustmentTime;

        /**
         * 优先级
         */
        private Integer priority;
    }

    @Getter
    @Setter
    public static class MassSetting {

        @NotBlank(message = "量产限置不能为空")
        @CodeDict(value = "taskInspectionStatus", message = "量产限置值错误")
        private String firstInspectStatus;

        @NotBlank(message = "检验类型编码不能为空")
        private String resourceCode;
    }
}
