package com.chinajey.dwork.modules.warehousingApplicant.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreDimension {

    private String materialCode;

    private String warehouseCode;
}
