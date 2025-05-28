package groovy.aps

import com.chinajay.virgo.bmf.obj.BmfObject
import com.chinajay.virgo.bmf.sql.Conjunction
import com.chinajay.virgo.bmf.sql.OperationType
import com.chinajay.virgo.bmf.sql.Restriction
import com.chinajay.virgo.utils.BmfUtils
import com.chinajay.virgo.utils.SpringUtils
import com.tengnat.dwork.modules.aps.domain.dto.ApsDto
import com.tengnat.dwork.modules.script.abstracts.ApsNodeGroovyClass
import com.tengnat.dwork.modules.script.service.BasicGroovyService
import com.tengnat.dwork.modules.script.service.SceneGroovyService
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang3.StringUtils

import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

/**
 * 迈金搬过来的，磊哥写的
 *
 * 设备规则
 * 1)根据工序下设置设备组进行选定
 * 2)根据设备组下的设备进行生产任务绑定
 * 3)可用的设备及标准产能及优先级，对工单进行1/N个设备进行绑定
 * 4)针对相同的设备的生产任务，优先考虑计划结束时间不变的情况下是否满足生产时间>=优先生产的生产任务结束时间+1，若不满足则更换可用设备
 */
class ApsEquipmentScheduling extends ApsNodeGroovyClass {
    BasicGroovyService basicGroovyService = SpringUtils.getBean(BasicGroovyService.class)
    SceneGroovyService sceneGroovyService = SpringUtils.getBean(SceneGroovyService.class)
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    // 新增设备每日使用记录（设备编码|日期 -> 已用小时数）
    Map<String, BigDecimal> equipDailyUsage = new HashMap<>()
    // 结构改为：设备编码|日期|班次编码 -> 已用小时数
    Map<String, BigDecimal> equipShiftUsage = new HashMap<>()
    //设备绑定工位
    Map<String, BmfObject> equipBindingStation = new HashMap<>()
    Map<String, BmfObject> stationBindingObj = new HashMap<>()
    Map<String, List<BmfObject>> objBindingClass = new HashMap<>()
    BmfObject stationResource = null
    BmfObject otherSetting = null
    static Map<String, String> bmfClassSubKey = new HashMap<>()
    static {
        bmfClassSubKey.put("workshop", "workshopProduceClasses")
        bmfClassSubKey.put("costCenter", "code")
        bmfClassSubKey.put("workCenter", "code")
    }

    @Override
    List<ApsDto> runScript(List<ApsDto> apsInfos) {
        // 清空历史记录
        equipDailyUsage.clear()
        //查询工位资源对象
        stationResource = basicGroovyService.findOne("resource", Collections.singletonMap("code", "station"))
        List<BmfObject> otherSettings = basicGroovyService.find("otherSettings")
        if (CollectionUtils.isNotEmpty(otherSettings)) {
            otherSetting = otherSettings.get(0)
        }

        for (ApsDto apsDto : apsInfos) {
            apsDto.getProcesses().sort(Comparator.comparingInt(ApsDto.Processe::getProcessNo))
            for (ApsDto.Processe process : apsDto.getProcesses()) {
                Date originalPlanEndTime = process.getPlanEndTime()
                Date lastEndTime = null
                List<BmfObject> equipmentList = getProcessEquipment(process)
                if (CollectionUtils.isEmpty(equipmentList)) continue

                List<ApsDto.BoxOrderProcessProduceTask> newTasks = new ArrayList<>()
                BigDecimal remainingQty = process.getPlanQuantity()

                // 关键修改：按天循环，而非按设备循环
                int dayOffset = 0
                while (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    // 获取当日所有设备的可用产能
                    BigDecimal dailyCapacity = calculateDailyTotalCapacity(equipmentList, dayOffset)
                    if (dailyCapacity <= 0) {
                        dayOffset++
                        continue
                    }

                    // 分配当日的生产量
                    BigDecimal allocatedQty = remainingQty.min(dailyCapacity)
                    remainingQty = remainingQty.subtract(allocatedQty)
                    // 遍历所有设备分配具体任务
                    for (BmfObject equipment : equipmentList) {
                        List<BmfObject> shifts = getMergedShifts(equipment)
                        BigDecimal capacity = equipment.getBigDecimal("capacity")

                        for (BmfObject shift : shifts) {
                            AllocationResult result = allocateShiftCapacity(
                                    process, equipment, shift,
                                    allocatedQty, capacity, dayOffset
                            )
                            if (result.getAllocatedQty() > 0) {
                                allocatedQty = allocatedQty.subtract(result.getAllocatedQty())
                                def task = createShiftTask(process, equipment.getString("resourceCode"), shift, result.getAllocatedQty(), result.getTaskDate())
                                newTasks.add(task)
                            }
                        }
                    }


                    dayOffset++
                }
                process.setProduceTasks(newTasks)
                updateProcessSchedule(process, lastEndTime, originalPlanEndTime, apsDto)
            }
        }
        return apsInfos
    }

    // 分配具体班次产能
    private AllocationResult allocateShiftCapacity(ApsDto.Processe process,
                                                   BmfObject equipment,
                                                   BmfObject shift,
                                                   BigDecimal remainingDailyQty,
                                                   BigDecimal capacity,
                                                   int dayOffset) {
        String key = "${equipment.resourceCode}|${dayOffset}|${shift.code}"
        BigDecimal availableHours = getShiftHours(shift) - equipShiftUsage.getOrDefault(key, BigDecimal.ZERO)

        BigDecimal allocatableQty = (availableHours * capacity)
                .min(remainingDailyQty)
                .setScale(0, RoundingMode.HALF_UP)

        if (allocatableQty > 0) {
            BigDecimal usedHours = allocatableQty.divide(capacity, 6, RoundingMode.HALF_UP)
            equipShiftUsage.put(key, equipShiftUsage.getOrDefault(key, BigDecimal.ZERO) + usedHours)

            return new AllocationResult(allocatableQty,
                    new Date(process.planStartTime.getTime()).toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                            .plusDays(dayOffset)
                            .atStartOfDay()
                            .toDate())
        }

        return new AllocationResult(BigDecimal.ZERO, null)
    }

    // 计算当日所有设备的总产能
    private BigDecimal calculateDailyTotalCapacity(List<BmfObject> equipmentList, int dayOffset) {
        BigDecimal totalCapacity = BigDecimal.ZERO

        equipmentList.each { equipment ->
            List<BmfObject> shifts = getMergedShifts(equipment)
            shifts.each { shift ->
                String key = "${equipment.resourceCode}|${dayOffset}|${shift.code}"
                BigDecimal usedHours = equipShiftUsage.getOrDefault(key, BigDecimal.ZERO)
                BigDecimal availableHours = getShiftHours(shift) - usedHours
                totalCapacity += availableHours * equipment.capacity
            }
        }

        return totalCapacity
    }

    // 新增方法：获取合并后的班次（处理重叠班次）
    private List<BmfObject> getMergedShifts(BmfObject equipmentResource) {
        BmfObject bindingStation = findEquipBindingStation(equipmentResource.getString("resourceCode"))
        if (bindingStation == null) {
            def shift = new BmfObject()
            shift.put("classCode", "0001")
            BmfObject shiftTimes = new BmfObject()
            shiftTimes.put("beginTime", "00:00")
            shiftTimes.put("endTime", "23:59")
            List<BmfObject> shiftTimeList = Collections.singletonList(shiftTimes)
            shift.put("classesTimes", shiftTimeList)
            return Collections.singletonList(shift)
        }
        BmfObject bindingObj = findStationBindingObj(bindingStation.getString("bindingResourceCode"))
        if (bindingObj == null) {
            def shift = new BmfObject()
            shift.put("classCode", "0001")
            BmfObject shiftTimes = new BmfObject()
            shiftTimes.put("beginTime", "00:00")
            shiftTimes.put("endTime", "23:59")
            List<BmfObject> shiftTimeList = Collections.singletonList(shiftTimes)
            shift.put("classesTimes", shiftTimeList)
            return Collections.singletonList(shift)
        }
        List<BmfObject> produceClasses = findObjBindingClass(bindingObj.getString("bindingResourceCode"))


//        BmfObject equipment = basicGroovyService.findOne("equipment", "code", equipmentResource.getString("resourceCode"))
//        if (equipment == null) {
//            def shift = new BmfObject()
//            shift.put("classCode", "0001")
//            return Collections.singletonList(shift)
//        }
//        BmfObject department = equipment.getAndRefreshBmfObject("department")
//        if (department == null) {
//            def shift = new BmfObject()
//            shift.put("classCode", "0001")
//            return Collections.singletonList(shift)
//        }
//        List<BmfObject> departmentProduceClasses = department.getAndRefreshList("departmentProduceClasses")
//        if (departmentProduceClasses == null) {
//            def shift = new BmfObject()
//            shift.put("classCode", "0001")
//            return Collections.singletonList(shift)
//        }
//        List<BmfObject> produceClasses = BmfUtils.batchRefreshAttribute(departmentProduceClasses, "produceClasses")
//        List<BmfObject> produceClassesTimes = BmfUtils.batchRefreshAttribute(produceClasses, "classesTimes")
//        for (BmfObject produceClass : produceClasses) {
//            List<BmfObject> classesTimes = produceClass.getList("classesTimes")
//            for (BmfObject classesTime : classesTimes) {
//                classesTime.put("classCode", produceClass.getString("code"))
//                classesTime.put("className", produceClass.getString("name"))
//            }
//        }
//        if (CollectionUtils.isEmpty(produceClassesTimes)) {
//            def shift = new BmfObject()
//            shift.put("classCode", "0001")
//            produceClassesTimes = Collections.singletonList(shift)
//        }
        return produceClasses;
    }

    // 重构方法：创建班次任务
    private ApsDto.BoxOrderProcessProduceTask createShiftTask(
            ApsDto.Processe process,
            String equipmentCode,
            BmfObject shift,
            BigDecimal quantity,
            Date taskDate
    ) {
        // 计算具体时间段
        LocalDate baseDate = taskDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

        //        LocalTime shiftStart = StringUtils.isNotEmpty(shift.getString("beginTime")) ? LocalTime.parse(shift.getString("beginTime")) : LocalTime.MIN
//        LocalTime shiftEnd = StringUtils.isNotEmpty(shift.getString("endTime")) ? LocalTime.parse(shift.getString("endTime")) : LocalTime.MAX

//        Date taskStart = Date.from(
//                baseDate.atTime(shiftStart)
//                        .atZone(ZoneId.systemDefault())
//                        .toInstant()
//        )
        Date taskStart = baseDate.toDate()
//
//                Date taskEnd = Date.from(
//                baseDate.atTime(shiftEnd)
//                        .atZone(ZoneId.systemDefault())
//                        .toInstant()
//        )
        Date taskEnd = baseDate.toDate()

        createNewTask(process.getProduceTasks().get(0), quantity, equipmentCode, taskStart, taskEnd, shift)
    }

    List<BmfObject> getProcessEquipment(ApsDto.Processe process) {
        BmfObject processObj = this.basicGroovyService.findOne("boxOrderProcess", process.getId())
        List<BmfObject> processResourceList = processObj.getAndRefreshList("processResources")
        List<BmfObject> equipmentList = new ArrayList<>()
        if (CollectionUtils.isNotEmpty(processResourceList)) {
            // 筛选工艺资源下的设备并按优先级排序
            equipmentList = processResourceList.stream()
                    .filter(processResource -> processResource.getBigDecimal("capacity") != null
                            && "equipment" == processResource.getString("type"))
                    .sorted(Comparator.comparingInt(processResource -> ((BmfObject) processResource).getInteger("priority")))
                    .collect(Collectors.toList())
        }
        return equipmentList
    }

    private ApsDto.BoxOrderProcessProduceTask createNewTask(ApsDto.BoxOrderProcessProduceTask dto, BigDecimal quantity,
                                                            String equipmentCode, Date planStartTime,
                                                            Date planEndTime, BmfObject shift) {
        BmfObject bindingStation = findEquipBindingStation(equipmentCode)
        ApsDto.BoxOrderProcessProduceTask task = new ApsDto.BoxOrderProcessProduceTask()
        task.setResourceCode(bindingStation != null ? bindingStation.getString("bindingResourceCode") : null)
        task.setResourceName(bindingStation != null ? bindingStation.getString("bindingResourceName") : null)
        task.setResource(stationResource)
        task.setPlanStartTime(planStartTime)
        task.setPlanEndTime(planEndTime)

        task.setQuantity(quantity)
        task.setPlanQuantity(quantity)
        task.setQualifiedQuantity(BigDecimal.ZERO)
        task.setDisQualifiedQuantity(BigDecimal.ZERO)
        task.setProcessNo(dto.getProcessNo())
        task.setProcessCode(dto.getProcessCode())
        task.setProcessName(dto.getProcessName())
        task.setSortNum(dto.getSortNum())
        task.setStatus(dto.getStatus())
        task.setUnit(dto.getUnit())
        task.setContinuousProcess(dto.getContinuousProcess())
        task.setBoxOrderProcess(dto.getBoxOrderProcess())
        task.setBoxOrderCode(dto.getBoxOrderCode())
        task.setClasses(shift.getString("classCode"))
        task.setClassesName(shift.getString("className"))
        return task
    }

    // 辅助方法：获取班次时长
    private BigDecimal getShiftHours(BmfObject shift) {
        long hours = 0
        List<BmfObject> classesTimes = shift.getList("classesTimes")
        for (BmfObject classesTime : classesTimes) {
            if (StringUtils.isEmpty(classesTime.getString("beginTime")) && StringUtils.isEmpty(classesTime.getString("endTime"))) {
                continue
            }
            LocalTime start = LocalTime.parse(classesTime.getString("beginTime"), formatter)
            LocalTime end = LocalTime.parse(classesTime.getString("endTime"), formatter)
            hours += Duration.between(start, end).toHours()
        }
        return BigDecimal.valueOf(hours)
    }

    // 新增方法：更新工序计划时间与延期标记
    private void updateProcessSchedule(ApsDto.Processe process,
                                       Date lastEndTime,
                                       Date originalPlanEndTime,
                                       ApsDto apsDto) {
        // 设置工序实际结束时间
        process.setPlanEndTime(lastEndTime != null ? lastEndTime : originalPlanEndTime)

        // 判断是否延期
        boolean isDelayed = (lastEndTime != null && lastEndTime > originalPlanEndTime)

        // 更新APS对象状态
        apsDto.setPlanEndTime(isDelayed ? lastEndTime : originalPlanEndTime)
        apsDto.setFlag(isDelayed)
    }

    private BmfObject findEquipBindingStation(String equipmentCode) {
        if (equipBindingStation.containsKey(equipmentCode)) {
            return equipBindingStation.get(equipmentCode)
        }
        List<BmfObject> bindingResourceList = sceneGroovyService.getResourceBinding("equipment", equipmentCode, "station")
        if (CollectionUtils.isNotEmpty(bindingResourceList)) {
            equipBindingStation.put(equipmentCode, bindingResourceList.get(0))
            return bindingResourceList.get(0)
        } else {
            equipBindingStation.put(equipmentCode, null)
            return null
        }
    }

    private BmfObject findStationBindingObj(String stationCode) {
        if (stationBindingObj.containsKey(stationCode)) {
            return stationBindingObj.get(stationCode)
        }
        List<BmfObject> bindingResourceList = sceneGroovyService.getResourceBinding("station", stationCode, otherSetting.getString("belongOrg"))
        if (CollectionUtils.isNotEmpty(bindingResourceList)) {
            equipBindingStation.put(stationCode, bindingResourceList.get(0))
            return bindingResourceList.get(0)
        } else {
            equipBindingStation.put(stationCode, null)
            return null
        }
    }

    private List<BmfObject> findObjBindingClass(String objCode) {
        if (objBindingClass.containsKey(objCode)) {
            return objBindingClass.get(objCode)
        }
        List<BmfObject> bindingResourceList = sceneGroovyService.getResourceBinding(otherSetting.getString("belongOrg"), objCode, "produceClasses")
        if (CollectionUtils.isNotEmpty(bindingResourceList)) {
            List<Object> resourceCodes = bindingResourceList.stream().map(item -> item.getString("bindingResourceCode")).collect(Collectors.toList())
            List<BmfObject> produceClasses = basicGroovyService.find("produceClasses", Collections.singletonList(
                    Restriction.builder().conjunction(Conjunction.AND).attributeName("code").operationType(OperationType.IN).values(resourceCodes).build()
            ))
            def produceClassesTimes = BmfUtils.batchRefreshAttribute(produceClasses, "classesTimes")
            for (BmfObject produceClass : produceClasses) {
                List<BmfObject> classesTimes = produceClass.getList("classesTimes")
                for (BmfObject classesTime : classesTimes) {
                    classesTime.put("classCode", produceClass.getString("code"))
                    classesTime.put("className", produceClass.getString("name"))
                }
            }
            objBindingClass.put(objCode, produceClasses)
            return produceClasses
        } else {
            objBindingClass.put(objCode, null)
            return null
        }
    }

    static class AllocationResult {
        private final BigDecimal allocatedQty;
        private final Date taskDate;

        public AllocationResult(BigDecimal qty, Date date) {
            this.allocatedQty = qty;
            this.taskDate = date;
        }

        public BigDecimal getAllocatedQty() {
            return allocatedQty;
        }

        public Date getTaskDate() {
            return taskDate;
        }
    }
}