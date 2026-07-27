package com.njupt.aiassistant.exception;

import com.njupt.aiassistant.common.ApiResponse;
import com.njupt.aiassistant.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MissingRequestHeaderException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        var error = exception.getErrorCode();
        var status = resolveStatus(error);
        return ResponseEntity.status(status)
                .body(ApiResponse.failure(error.getCode(), exception.getMessage()));
    }

    private HttpStatus resolveStatus(ErrorCode error) {
        if (error.getCode() >= 40100 && error.getCode() < 40200) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (error.getCode() >= 40400 && error.getCode() < 40500) {
            return HttpStatus.NOT_FOUND;
        }
        if (error.getCode() >= 40900 && error.getCode() < 41000) {
            return HttpStatus.CONFLICT;
        }
        if (error.getCode() >= 40300 && error.getCode() < 40400) {
            return HttpStatus.FORBIDDEN;
        }
        if (error.getCode() >= 42900 && error.getCode() < 43000) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (error.getCode() >= 50200 && error.getCode() < 50300) {
            return HttpStatus.BAD_GATEWAY;
        }
        if (error.getCode() >= 50300 && error.getCode() < 50400) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (error.getCode() == 500
                || (error.getCode() >= 50000 && error.getCode() < 60000)) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> handleBindingException(Exception exception) {
        var bindingResult = exception instanceof MethodArgumentNotValidException methodException
                ? methodException.getBindingResult()
                : ((BindException) exception).getBindingResult();
        var message = bindingResult.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST.getCode(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.BAD_REQUEST.getCode(), "请求体格式不正确"));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure(ErrorCode.FILE_EMPTY.getCode(), "缺少上传文件"));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
            MissingRequestHeaderException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure(
                        ErrorCode.ANONYMOUS_SESSION_REQUIRED.getCode(),
                        ErrorCode.ANONYMOUS_SESSION_REQUIRED.getMessage()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failure(
                        ErrorCode.FILE_TOO_LARGE.getCode(),
                        ErrorCode.FILE_TOO_LARGE.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled server exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()
                ));
    }
}
