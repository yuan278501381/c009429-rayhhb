package groovy.produce

import com.alibaba.fastjson.JSONObject
import com.chinajay.virgo.utils.SpringUtils
import com.chinajey.application.common.exception.BusinessException
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ConfigNode
import com.tengnat.dwork.modules.manufacturev2.domain.dto.ValidateRuleResult
import com.tengnat.dwork.modules.manufacturev2.domain.entity.DwkStationReal
import com.tengnat.dwork.modules.manufacturev2.mapper.ProduceExecuteCommonMapper
import com.tengnat.dwork.modules.script.abstracts.ProduceValidateGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService

import java.text.SimpleDateFormat
import java.time.LocalTime


/***
 * 开始生产-设备点检检查
 * 1、检查设备必填
 * 2、并校验该设备在当班内(班次主数据中设置的白班、晚班起止时间点)是否有设备点检记录（白班、晚班各至少完成一次点检），如果有则通过,如果没有则报错
 *
 * */
class ProduceValidateLoginEquipmentCheck extends  ProduceValidateGroovyClass {

    BasicGroovyService basicGroovyService = SpringUtils.getBean("basicGroovyService")



    @Override
    ValidateRuleResult login(DwkStationReal stationReal, ConfigNode configNode, JSONObject jsonObject) {
        // 当前登记的设备,Login

        def firstResource = jsonObject.getJSONArray("resources")
                .toJavaList(Map.class)
                .stream()
                .findFirst()
                .orElse(null)

        String equipmentCode = firstResource?.get("resourceCode")?.toString()

       //当前的工位代码
        String stationCode = stationReal.getStationCode()

        if (!equipmentCode) {
            throw new BusinessException("工位【${stationCode}】,设备必须登记")
        }


        //查当前设备的设备点检记录,查找逻辑,按当前时间,找到班次(是一个范围,比如晚8点到第2天早8点),然后按班次的时间范围,
        //找出范围内的点检记录(状态为已完成-completed,处理结果为通过-pass),如果找到一个则通过,否则报错

       //获得当前时间
        Date now = new Date()

        //获得白班\晚班的起止时间
        String sSQLFetchTimebydayShift='select t.category,t1.begin_time,t1.end_time from dwk_produce_classes t\n'
        sSQLFetchTimebydayShift+='inner join dwk_produce_classes_time t1 on t.id=t1.classes_id\n'
        sSQLFetchTimebydayShift+='where t.is_delete=false and t1.is_delete=false'

        def sqlResult1= basicGroovyService.findList(sSQLFetchTimebydayShift)

        String nowStr = new SimpleDateFormat("HH:mm").format(now)
        LocalTime nowTime = LocalTime.parse(nowStr)

        String currentShift = null
        String shiftBeginStr = null
        String shiftEndStr = null

        sqlResult1.each { row ->
            def category = row["category"]
            def beginStr = row["begin_time"]
            def endStr = row["end_time"]

            def begin = LocalTime.parse(beginStr)
            def end = LocalTime.parse(endStr)

            boolean isInShift
            if (begin.isBefore(end)) {
                // 非跨天（如白班：08:00~20:00）
                isInShift = !nowTime.isBefore(begin) && nowTime.isBefore(end)
            } else {
                // 跨天（如夜班：20:00~08:00）
                isInShift = !nowTime.isBefore(begin) || nowTime.isBefore(end)
            }

            if (isInShift) {
                currentShift = category
                shiftBeginStr = beginStr
                shiftEndStr = endStr
            }
        }

        if (currentShift == null) {
            throw new BusinessException("当前时间不在任何班次内,请检查班次主数据的设置!")
        }


        //时间转换为日期,考虑跨天的情况
        def todayStr = new SimpleDateFormat("yyyy-MM-dd").format(now)
        def shiftBeginDateTime = "${todayStr} ${shiftBeginStr}"
        def shiftEndDateTime = "${todayStr} ${shiftEndStr}"

// 如果是跨天班次（夜班），要加一天
        if (LocalTime.parse(shiftBeginStr).isAfter(LocalTime.parse(shiftEndStr))) {
            def cal = Calendar.getInstance()
            cal.time = now
            cal.add(Calendar.DATE, 1)
            def tomorrowStr = new SimpleDateFormat("yyyy-MM-dd").format(cal.time)
            shiftEndDateTime = "${tomorrowStr} ${shiftEndStr}"
        }
        //查询当前班次时间内的设备点检记录
        String sSQL = ""
        sSQL += "select 1\n"
        sSQL += "from self_equipment_tally_sheet t\n"
        sSQL += "where is_delete = false\n"
        sSQL += "  and t.status = 'completed'\n"
        sSQL += "  and t.handle_result = 'pass'\n"
        sSQL += "  and t.transaction_code = 'TR0003'\n"
        sSQL += "  and t.ledger_code = '${equipmentCode}'\n"
        sSQL += "  and t.completion_time between '${shiftBeginDateTime}' and '${shiftEndDateTime}'"

        def sqlResult2= basicGroovyService.findOne(sSQL)

        if(!sqlResult2)
        {
            throw new BusinessException("当前班次没有设备【${equipmentCode}】的点检记录,请检查后重试！!")
        }

        return ValidateRuleResult.success()
    }
}
