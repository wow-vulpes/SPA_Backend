package com.example.spa.error;

import com.example.spa.movement.MovementException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.exc.MismatchedInputException;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
  @ExceptionHandler(MovementException.class)
  public ResponseEntity<ApiError> movement(MovementException error) {
    int status =
        switch (error.getKind()) {
          case INVALID -> 422;
          case NOT_FOUND -> 404;
          case CONFLICT -> 409;
        };
    return ResponseEntity.status(status)
        .body(
            new ApiError(
                status,
                error.getCode(),
                error.getMessage(),
                error.getAvailableQuantity(),
                List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException error) {
    List<ApiError.FieldViolation> violations =
        error.getBindingResult().getFieldErrors().stream()
            .map(
                field ->
                    new ApiError.FieldViolation(
                        snakeCase(field.getField()), field.getDefaultMessage()))
            .toList();
    return ResponseEntity.status(422)
        .body(new ApiError(422, "VALIDATION_FAILED", "Проверьте поля запроса", null, violations));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof MismatchedInputException)
        return error(422, "INVALID_FIELD_FORMAT", "Некорректный тип или формат поля");
    }
    return error(400, "MALFORMED_JSON", "Необходим корректный JSON-объект");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> integrity(DataIntegrityViolationException error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraint
          && "uq_movement_document".equals(constraint.getConstraintName())) {
        return movement(MovementException.duplicateDocument());
      }
    }
    return unexpected(error);
  }

  @ExceptionHandler({PessimisticLockingFailureException.class, TransactionTimedOutException.class})
  public ResponseEntity<ApiError> busy(RuntimeException ignored) {
    return error(409, "POSITION_BUSY", "Позиция занята другой операцией; повторите запрос");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ignored) {
    return error(405, "METHOD_NOT_ALLOWED", "Метод не поддерживается");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiError> media(HttpMediaTypeNotSupportedException ignored) {
    return error(415, "UNSUPPORTED_MEDIA_TYPE", "Используйте application/json");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiError> missing(NoResourceFoundException ignored) {
    return error(404, "RESOURCE_NOT_FOUND", "Ресурс не найден");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception error) {
    log.error("Unexpected request failure", error);
    return error(500, "INTERNAL_ERROR", "Внутренняя ошибка сервера");
  }

  private ResponseEntity<ApiError> error(int status, String code, String message) {
    return ResponseEntity.status(status).body(new ApiError(status, code, message, null, List.of()));
  }

  private String snakeCase(String field) {
    return field.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
  }
}
