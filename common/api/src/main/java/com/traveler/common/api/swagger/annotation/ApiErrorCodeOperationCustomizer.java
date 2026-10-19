package com.traveler.common.api.swagger.annotation;

import com.traveler.common.core.code.BaseErrorCode;
import com.traveler.common.core.code.ErrorCode;
import com.traveler.common.core.response.ErrorResponse;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;

@Slf4j
public class ApiErrorCodeOperationCustomizer implements OperationCustomizer {

    private static final String JSON_MEDIA_TYPE = "application/json";
    private static final String RESPONSE_DTO_REF = "#/components/schemas/ApiResponse";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        // 1. @SwaggerErrorResponse가 붙은 모든 어노테이션을 추출
        List<Annotation> errorAnnotations = Stream.of(handlerMethod.getMethod().getAnnotations())
                .filter(ann -> ann.annotationType().isAnnotationPresent(SwaggerErrorResponse.class))
                .toList();

        if (errorAnnotations.isEmpty()) {
            return operation;
        }

        // 2. Operation에서 Responses 확보 (null 방어)
        ApiResponses responses = Optional.ofNullable(operation.getResponses()).orElseGet(ApiResponses::new);
        operation.setResponses(responses);

        // 3. 에러 코드 추출 및 주입
        for (Annotation annotation : errorAnnotations) {
            extractErrorCodes(annotation).forEach(errorCode -> addErrorCodeExample(responses, errorCode));
        }

        return operation;
    }

    /** 리플렉션을 통해 어노테이션의 모든 필드에서 BaseErrorCode[]를 추출 */
    private List<BaseErrorCode> extractErrorCodes(Annotation annotation) {
        List<BaseErrorCode> errorCodes = new ArrayList<>();
        Method[] methods = annotation.annotationType().getDeclaredMethods();

        for (Method method : methods) {
            // 반환 타입이 BaseErrorCode 배열인지 체크
            if (BaseErrorCode[].class.isAssignableFrom(method.getReturnType())) {
                try {
                    BaseErrorCode[] result = (BaseErrorCode[]) method.invoke(annotation);
                    if (result != null) {
                        errorCodes.addAll(Arrays.asList(result));
                    }
                } catch (Exception e) {
                    log.error(
                            "Swagger customizer 리플렉션 오류 [Annotation: {}, Method: {}]: {}",
                            annotation.annotationType().getSimpleName(),
                            method.getName(),
                            e.getMessage(),
                            e);
                }
            }
        }
        return errorCodes;
    }

    private void addErrorCodeExample(ApiResponses responses, BaseErrorCode errorCode) {
        String statusCode = String.valueOf(errorCode.getStatus());
        ExampleHolder holder = createExampleHolder(errorCode);

        ApiResponse apiResponse = responses.computeIfAbsent(statusCode, code -> new ApiResponse());

        Content content = Optional.ofNullable(apiResponse.getContent()).orElseGet(Content::new);
        apiResponse.setContent(content);

        MediaType mediaType = Optional.ofNullable(content.get(JSON_MEDIA_TYPE)).orElseGet(() -> {
            MediaType mt = new MediaType();
            mt.setSchema(new Schema<>().$ref(RESPONSE_DTO_REF));
            content.addMediaType(JSON_MEDIA_TYPE, mt);
            return mt;
        });

        mediaType.addExamples(holder.name(), holder.holder());
    }

    private ExampleHolder createExampleHolder(BaseErrorCode errorCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("code", errorCode.getCode());
        body.put("message", errorCode.getMessage());

        // Validation 에러인 경우 result 에 Sample 데이터 삽입
        if (errorCode == ErrorCode.INVALID_TYPE_VALUE) {
            body.put(
                    "result",
                    List.of(ErrorResponse.builder()
                            .field("parameterName")
                            .value("rejectedValue")
                            .reason("해당 필드의 제약 조건 위반 사유 (ex: 필수 값 누락, 규격 불일치 등)")
                            .build()));
        } else {
            body.put("result", null);
        }

        String errorName = errorCode.toString();

        // Swagger Example 객체 생성
        Example example = new Example();
        example.setSummary(errorName); // 드롭다운에 표시될 이름
        example.setValue(body); // 위에서 만든 Map이 JSON으로 출력

        return ExampleHolder.builder()
                .name(errorName)
                .status(errorCode.getStatus()) // HttpStatus의 숫자값 (예: 400)
                .code(errorCode.getCode())
                .holder(example)
                .build();
    }
}
