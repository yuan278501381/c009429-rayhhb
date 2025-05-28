package com.chinajey.dwork.common;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author ChenYang
 * date 2023-02-16
 * BigDecimal转JSON 精度转换
 */

public class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal bigDecimal, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        if (bigDecimal != null) {
            BigDecimal number = bigDecimal.setScale(6, BigDecimal.ROUND_UP);
            jsonGenerator.writeNumber(number);
        } else {
            jsonGenerator.writeNumber(bigDecimal);
        }
    }
}
