package com.loan.loan.service;

import com.loan.loan.dto.JudgmentDTO.Request;
import com.loan.loan.dto.JudgmentDTO.Response;

public interface JudgmentService {

    Response create(Request request);
}