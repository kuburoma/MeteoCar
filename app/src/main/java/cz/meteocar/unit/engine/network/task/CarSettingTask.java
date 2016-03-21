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
import cz.meteocar.unit.engine.network.dto.CarSettingDto;
import cz.meteocar.unit.engine.network.dto.CreateCarSettingRequest;
import cz.meteocar.unit.engine.network.dto.GetCarSettingResponse;
import cz.meteocar.unit.engine.network.task.converter.CarSettingsEntity2DtoConverter;
import cz.meteocar.unit.engine.storage.helper.CarSettingHelper;
import cz.meteocar.unit.engine.storage.model.CarSettingEntity;

/**
 * Created by Nell on 20.3.2016.
 */
public class CarSettingTask extends TimerTask {

    private NetworkConnector<Void, GetCarSettingResponse> getConnector = new NetworkConnector<>(Void.class, GetCarSettingResponse.class, "carSettings");
    private NetworkConnector<CreateCarSettingRequest, Void> postConnector = new NetworkConnector<>(CreateCarSettingRequest.class, Void.class, "carSettings");

    private CarSettingHelper dao = ServiceManager.getInstance().db.getCarSettingHelper();

    private static final Converter<CarSettingEntity, CarSettingDto> converterForward = new CarSettingsEntity2DtoConverter();
    private static final Converter<CarSettingDto, CarSettingEntity> converterBackward = converterForward.reverse();

    @Override
    public void run() {
        if (isNetworkReady()) {
            try {
                List<CarSettingEntity> all = dao.getAll();
                Long updateTime = getLatestUpdateTime(all);

                List<QueryParameter> params = new ArrayList<>();
                params.add(new QueryParameter("lastUpdateTime", String.valueOf(updateTime)));
                GetCarSettingResponse response = getConnector.get(null, params);
                if (response.getRecords().size() == 0) {
                    return;
                }
                dao.deleteAll();
                dao.saveAll(Lists.newArrayList(converterBackward.convertAll(response.getRecords())));
            } catch (NetworkException e) {
                if (ErrorCodes.RECORDS_UPDATE_REQUIRED.toString().equals(e.getErrorResponse().getCode())) {
                    try {
                        postConnector.post(new CreateCarSettingRequest(Lists.newArrayList(converterForward.convertAll(dao.getAll()))));
                    } catch (NetworkException e1) {
                        ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
                    }
                }
                ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
            }
        } else {
            Log.d(AppLog.LOG_TAG_NETWORK, "CarSettingTask: network is offline");
        }

    }

    protected boolean isNetworkReady() {
        return ServiceManager.getInstance().network.isOnline();
    }

    protected Long getLatestUpdateTime(List<CarSettingEntity> CarSettingEntities) {
        Long max = 0L;
        for (CarSettingEntity entity : CarSettingEntities) {
            if (max < entity.getUpdateTime()) {
                max = entity.getUpdateTime();
            }
        }
        return max;
    }
}

