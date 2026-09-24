package com.traveler.useractivity.domain.history.document;

import com.traveler.useractivity.domain.history.document.vo.HistoryFailInfo;
import java.time.Instant;
import java.util.Map;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

// 이 문서는 서비스가 아닌 외부 파이프라인(logstash)이 색인하므로 BaseDocument의
// createdAt/updatedAt(나노초 포맷)과 계약이 맞지 않아 상속하지 않고 직접 선언한다.
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Document(indexName = "#{@environment.getProperty('app.elasticsearch.indices.history')}")
@Setting(settingPath = "/elasticsearch/history-settings.json")
@Mapping(mappingPath = "/elasticsearch/history-mappings.json")
public class HistoryDocument {

    @Id
    private String id;

    // 정제 완료 시각(Logstash가 createdAt으로 채움)
    @Field(name = "@timestamp", type = FieldType.Date, format = DateFormat.strict_date_optional_time)
    private Instant timestamp;

    // 추적 ID
    @Field(type = FieldType.Keyword)
    private String traceId;

    // 프로세스 식별자
    @Field(name = "log_process_id", type = FieldType.Keyword)
    private Long logProcessId;

    @Field(name = "log_process_name", type = FieldType.Keyword)
    private String logProcessName;

    // 성공 여부
    @Field(type = FieldType.Boolean)
    private boolean success;

    // 에러 상세 정보
    @Field(name = "fail_info", type = FieldType.Object)
    private HistoryFailInfo failInfo;

    // 상세 로그 데이터
    @Field(name = "log_data", type = FieldType.Object)
    private Map<String, Object> logData;

    public static final class Fields {
        public static final String TRACE_ID = "traceId";
        public static final String LOG_PROCESS_ID = "log_process_id";
        public static final String SUCCESS = "success";
        public static final String FAIL_INFO = "fail_info";
        public static final String LOG_DATA = "log_data";

        public static final String FAIL_INFO_CODE = "fail_info.code";
        public static final String FAIL_INFO_STAGE = "fail_info.stage";
        public static final String FAIL_INFO_RULE_ID = "fail_info.fail_rule_id";
        public static final String FAIL_INFO_DETAIL = "fail_info.detail";

        public static final String ID = "id";
        public static final String TIMESTAMP = "@timestamp";
    }
}
