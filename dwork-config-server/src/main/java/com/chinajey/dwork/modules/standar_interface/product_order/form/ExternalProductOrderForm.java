package com.chinajey.dwork.modules.standar_interface.product_order.form;

import com.chinajey.dwork.common.annotation.CodeDict;
import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.ExtForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class ExternalProductOrderForm extends CodeForm {

    @NotBlank(message = "来源系统不能为空")
    @CodeDict(value = "sourceSystem", message = "来源系统值错误")
    private String sourceSystem;

    @NotBlank(message = "来源类型不能为空")
    @CodeDict(value = "documentType", message = "来源类型值错误")
    private String externalDocumentType;

    @NotBlank(message = "生产订单类型不能为空")
    @CodeDict(value = "productOrderTypes", message = "生产订单类型值错误")
    private String type;

    @NotBlank(message = "产品编码不能为空")
    private String productionCode;

    @NotBlank(message = "生产订单状态不能为空")
    @CodeDict(value = "productOrderStatus", message = "生产订单状态值错误")
    private String status;

    private String mainProductOrderCode;

    private String salesOrderCode;

    private String salesOrderLineNum;

    @NotNull(message = "计划数量不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "计划数量不能小于0")
    private BigDecimal planQuantity;

    @NotNull(message = "预计开工日期不能为空")
    private Date planStartTime;

    @NotNull(message = "预计完工日期不能为空")
    private Date planEndTime;

    @NotNull(message = "优先级不能为空")
    private Integer priority;

    @NotBlank(message = "工艺路线编码不能为空")
    private String processRouteCode;

    @NotBlank(message = "入库仓库编码不能为空")
    private String warehouseCode;

    @Valid
    private List<Material> materials;

    @Getter
    @Setter
    public static class Material extends ExtForm {

        @NotBlank(message = "行号不能为空")
        private String lineNum;

        @NotBlank(message = "物料编码不能为空")
        private String materialCode;

        @NotNull(message = "物料计划数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "物料计划数量不能小于0")
        private BigDecimal planQuantity;

        @NotNull(message = "物料基本用量不能为空")
        private BigDecimal basicUsage;

        @NotNull(message = "物料损耗率不能为空")
        private BigDecimal lossRate;

        @NotBlank(message = "物料发出仓库编码不能为空")
        private String warehouseCode;

        @NotNull(message = "物料工序号不能为空")
        private Integer processNo;
    }
}
