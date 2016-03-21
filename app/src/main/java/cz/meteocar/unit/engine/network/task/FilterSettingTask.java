package cz.meteocar.unit.engine.network.task;

import android.util.Log;

import com.google.common.base.Converter;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.event.ErrorViewType;
import cz.meteocar.unit.engine.event.NetworkErrorEvent;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.network.ErrorCodes;
import cz.meteocar.unit.engine.network.NetworkException;
import cz.meteocar.unit.engine.network.dto.CreateFilterSettingRequest;
import cz.meteocar.unit.engine.network.dto.FilterSettingDto;
import cz.meteocar.unit.engine.network.dto.GetFilterSettingResponse;
import cz.meteocar.unit.engine.network.task.converter.FilterSettingsEntity2DtoConverter;
import cz.meteocar.unit.engine.storage.helper.FilterSettingHelper;
import cz.meteocar.unit.engine.storage.model.FilterSettingEntity;

/**
 * Created by Nell on 20.3.2016.
 */
public class FilterSettingTask extends TimerTask {

    private NetworkConnector<Void, GetFilterSettingResponse> getConnector = new NetworkConnector<>(Void.class, GetFilterSettingResponse.class, "filterSettings");
    private NetworkConnector<CreateFilterSettingRequest, Void> postConnector = new NetworkConnector<>(CreateFilterSettingRequest.class, Void.class, "filterSettings");

    private FilterSettingHelper dao = ServiceManager.getInstance().db.getFilterSettingHelper();

    private static final Converter<FilterSettingEntity, FilterSettingDto> converterForward = new FilterSettingsEntity2DtoConverter();
    private static final Converter<FilterSettingDto, FilterSettingEntity> converterBackward = converterForward.reverse();

    @Override
    public void run() {
        if (isNetworkReady()) {
            try {
                List<FilterSettingEntity> all = dao.getAll();
                Long updateTime = getLatestUpdateTime(all);

                List<QueryParameter> params = new ArrayList<>();
                params.add(new QueryParameter("lastUpdateTime", String.valueOf(updateTime)));
                GetFilterSettingResponse response = getConnector.get(null, params);
                if (response.getRecords().size() == 0) {
                    return;
                }
                dao.deleteAll();
                dao.saveAll(Lists.newArrayList(converterBackward.convertAll(response.getRecords())));
            } catch (NetworkException e) {
                if (ErrorCodes.RECORDS_UPDATE_REQUIRED.toString().equals(e.getErrorResponse().getCode())) {
                    try {
                        postConnector.post(new CreateFilterSettingRequest(Lists.newArrayList(converterForward.convertAll(dao.getAll()))));
                    } catch (NetworkException e1) {
                        ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
                    }
                }
                ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
            }
        } else {
            Log.d(AppLog.LOG_TAG_NETWORK, "FilterSettingTask: network is offline");
        }

    }

    protected boolean isNetworkReady() {
        return ServiceManager.getInstance().network.isOnline();
    }

    protected Long getLatestUpdateTime(List<FilterSettingEntity> filterSettingEntities) {
        Long max = 0L;
        for (FilterSettingEntity entity : filterSettingEntities) {
            if (max < entity.getUpdateTime()) {
                max = entity.getUpdateTime();
            }
        }
        return max;
    }
}

