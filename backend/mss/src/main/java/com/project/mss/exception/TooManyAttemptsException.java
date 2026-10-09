package com.project.mss.exception;

/** Too many sign-in attempts: the caller has to wait. Answered as 429. */
public class TooManyAttemptsException extends RuntimeException {
  public TooManyAttemptsException(String message) {
    super(message);
  }
}
