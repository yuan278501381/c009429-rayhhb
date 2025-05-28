package com.chinajey.dwork.modules.purchaseOrder.form;

import com.chinajey.dwork.common.form.CodeForm;
import com.chinajey.dwork.common.form.ExtForm;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class PurchaseOrderForm extends CodeForm {
    @NotBlank(message = "来源系统不能为空")
    private String sourceSystem;

    @NotBlank(message = "供应商编码不能为空")
    private String providerCode;

    @NotBlank(message = "采购员编码不能为空")
    private String buyerCode;

    private String remark;

    @Valid
    @NotEmpty(message = "采购订单明细不能为空")
    private List<Detail> details;

    @Getter
    @Setter
    public static class Detail extends ExtForm {

        @NotBlank(message = "外部行号不能为空")
        private String lineNum;

        @NotBlank(message = "物料编码不能为空")
        private String materialCode;

        @NotNull(message = "计划数量不能为空")
        private BigDecimal planQuantity;

        @NotBlank(message = "目标仓库编码不能为空")
        private String warehouseCode;
    }
}
