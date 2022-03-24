package com.reencryptutility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sun.istack.NotNull;
import lombok.Data;

import javax.validation.Valid;

import java.time.LocalDateTime;

@Data
public class RequestWrapper<T> {
	private String id;
	private String version;
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
	private LocalDateTime requesttime;
	
	private Object metadata;
	
	@NotNull
	@Valid
	private T request;
}
