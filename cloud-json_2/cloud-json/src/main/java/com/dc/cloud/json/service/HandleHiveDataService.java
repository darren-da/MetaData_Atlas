package com.dc.cloud.json.service;

//import com.dc.cloud.json.dao.HiveDataDao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dc.cloud.json.bean.HiveData;
import com.dc.cloud.json.dao.HiveDataDao;
import com.dc.cloud.json.runner.HandleHiveDataRunner;
import com.dc.cloud.json.support.event.HandleJsonCompletionEvent;
import com.dc.cloud.json.support.event.HandleJsonEnum;
import com.dc.cloud.json.support.json.HiveDataJsonHandler;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.util.function.Tuple3;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Log4j2
@Service
public class HandleHiveDataService extends ServiceImpl<HiveDataDao, HiveData>
        implements ApplicationListener<HandleJsonCompletionEvent> {


    @Autowired
    HandleHiveDataRunner dataRunner;

    @Override
    public void onApplicationEvent(@NonNull HandleJsonCompletionEvent event) {
        if (event.getHandleJsonEnum().equals(HandleJsonEnum.HIVE_LINEAGE)) {
            List<HiveData> hiveDataList = event.getHiveData();
            doHandleChildGuid(hiveDataList);
            this.saveBatch(hiveDataList);
            log.info(event.getMessage());
        }
    }

    /**
     * service 执行子jsonUrl查询
     */
    private void doHandleChildGuid(List<HiveData> hiveDataList) {
        Map<String, Class<? extends HiveDataJsonHandler>> collect = hiveDataList.stream()
                .filter(hiveData -> StringUtils.hasText(hiveData.getGuid()))
                .map(HiveData::getRequestJsonTuple)
                .collect(Collectors.toMap(this::rebuildUrl, Tuple3::getT3));

        log.info("The " + hiveDataList.get(0).getTableName() + " details is start running");

        dataRunner.callHiveJsonInterface(Collections.synchronizedMap(collect));
    }

    private String rebuildUrl(Tuple3<String,String,Class<? extends HiveDataJsonHandler>> tuple3){
       return UriComponentsBuilder.fromUriString(tuple3.getT2())
                .queryParam("guid",tuple3.getT1())
                .build().toUriString();

    }




}
