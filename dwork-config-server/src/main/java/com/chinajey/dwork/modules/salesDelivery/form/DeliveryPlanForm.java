package com.chinajey.dwork.modules.salesDelivery.form;

import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.ExtForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Getter
@Setter
public class DeliveryPlanForm extends CodeForm {

    @NotBlank(message = "来源系统不能为空")
    private String sourceSystem;

    private Date orderDate;

    private Date deliveryDate;

    @NotBlank(message = "客户编码不能为空")
    private String customerCode;

    private String remark;

    @Valid
    @NotEmpty(message = "销售发货计划明细不能为空")
    private List<Detail> details;

    @Getter
    @Setter
    public static class Detail extends ExtForm {

        @NotBlank(message = "外部行号不能为空")
        private String lineNum;

        @NotBlank(message = "物料编码不能为空")
        private String materialCode;

        @NotNull(message = "待出库数量不能为空")
        private BigDecimal quantity;

        @NotBlank(message = "发出仓库编码不能为空")
        private String warehouseCode;
    }
}
