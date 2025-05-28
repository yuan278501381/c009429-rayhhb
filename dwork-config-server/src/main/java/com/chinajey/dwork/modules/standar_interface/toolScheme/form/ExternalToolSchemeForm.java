package com.chinajey.dwork.modules.standar_interface.toolScheme.form;

import com.chinajey.application.common.annotation.EnumValue;
import com.chinajey.application.common.exception.BusinessException;
import com.chinajey.dwork.common.form.CodeForm;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
public class ExternalToolSchemeForm extends CodeForm {

    private Long id;

    @NotBlank(message = "工器具方案名称不能为空")
    private String name;

    @NotBlank(message = "工艺路线编码不能为空")
    private String processRouteCode;

    private String processRouteName;

    private String materialCode;

    private String materialName;

    @NotBlank(message = "工序编码不能为空")
    private String processCode;

    private String processName;

    @NotNull(message = "是否默认不能为空")
    private Boolean isDefault;

    private String remark;

    @Valid
    private List<ToolSchemeItem> toolSchemeItems;

    @Getter
    @Setter
    public static class ToolSchemeItem {

        private Long id;

        private String resourceType;

        private String resourceCode;

        private String resourceName;

        @NotBlank(message = "器具类型不能为空")
        @EnumValue(strValues = {"knifeClassification", "moldClassification", "fixtureClassification", "jigClassification", "rackClassification", "sparePartsClassification"}, message = "器具类型不正确")
        private String toolType;

        @NotBlank(message = "物料编码不能为空")
        private String materialCode;

        private String materialName;

        private String specifications;

        @NotNull(message = "标准寿命不能为空")
        private Integer standardLife;

        @NotNull(message = "消耗系数不能为空")
        private BigDecimal consumptionCoefficient;

        @NotNull(message = "是否启用不能为空")
        private Boolean status;

    }

    public void validateItems() {
        if (CollectionUtils.isEmpty(toolSchemeItems)) {
            return;
        }
        for (ToolSchemeItem item : toolSchemeItems) {
            String resourceCode = item.getResourceCode();
            String resourceType = item.getResourceType();
            if (StringUtils.isBlank(resourceType)) {
                item.setResourceCode(null);
                item.setResourceName(null);
            } else {
                List<String> allowResourceTypes = Arrays.asList("equipmentGroup", "equipment");
                if (!allowResourceTypes.contains(resourceType)) {
                    throw new BusinessException("资源类型不正确");
                }
                if (StringUtils.isBlank(resourceCode)) {
                    throw new BusinessException("资源编码不能为空");
                }
            }
        }
    }
}
