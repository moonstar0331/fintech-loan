package com.loan.loan.service;

import com.loan.loan.domain.Application;
import com.loan.loan.domain.Entry;
import com.loan.loan.domain.Repayment;
import com.loan.loan.dto.BalanceDTO;
import com.loan.loan.dto.BalanceDTO.RepaymentRequest.RepaymentType;
import com.loan.loan.dto.RepaymentDTO.ListResponse;
import com.loan.loan.dto.RepaymentDTO.Request;
import com.loan.loan.dto.RepaymentDTO.Response;
import com.loan.loan.dto.RepaymentDTO.UpdateResponse;
import com.loan.loan.exception.BaseException;
import com.loan.loan.exception.ResultType;
import com.loan.loan.repository.ApplicationRepository;
import com.loan.loan.repository.EntryRepository;
import com.loan.loan.repository.RepaymentRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RepaymentServiceImpl implements RepaymentService {

    private final RepaymentRepository repaymentRepository;
    private final ApplicationRepository applicationRepository;
    private final EntryRepository entryRepository;
    private final BalanceService balanceService;
    private final ModelMapper modelMapper;

    @Override
    public Response create(Long applicationId, Request request) {

        // validation
        // 1. 계약을 완료한 신청 정보
        // 2. 집행이 되어 있어야 함
        if (!isRepayableApplication(applicationId)) {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        }

        Repayment repayment = modelMapper.map(request, Repayment.class);
        repayment.setApplicationId(applicationId);
        repaymentRepository.save(repayment);

        // 잔고 변경
        BalanceDTO.Response updatedBalance = balanceService.repaymentUpdate(applicationId,
                BalanceDTO.RepaymentRequest.builder()
                        .repaymentAmount(repayment.getRepaymentAmount())
                        .type(RepaymentType.REMOVE)
                        .build());

        Response response = modelMapper.map(repayment, Response.class);
        response.setBalance(updatedBalance.getBalance());

        return response;
    }

    @Override
    public List<ListResponse> get(Long applicationId) {
        List<Repayment> repayments = repaymentRepository.findAllByApplicationId(applicationId);

        return repayments.stream().map(r -> modelMapper.map(r, ListResponse.class)).collect(Collectors.toList());
    }

    @Override
    public UpdateResponse update(Long repaymentId, Request request) {
        Repayment repayment = repaymentRepository.findById(repaymentId).orElseThrow(() -> {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        });

        Long applicationId = repayment.getApplicationId();
        BigDecimal beforeRepaymentAmount = repayment.getRepaymentAmount();

        balanceService.repaymentUpdate(applicationId,
                BalanceDTO.RepaymentRequest.builder()
                        .repaymentAmount(beforeRepaymentAmount)
                        .type(RepaymentType.ADD)
                        .build());

        repayment.setRepaymentAmount(repayment.getRepaymentAmount());
        repaymentRepository.save(repayment);

        BalanceDTO.Response updatedBalance = balanceService.repaymentUpdate(applicationId,
                BalanceDTO.RepaymentRequest.builder()
                        .repaymentAmount(request.getRepaymentAmount())
                        .type(RepaymentType.REMOVE)
                        .build());

        return UpdateResponse.builder()
                .applicationId(applicationId)
                .beforeRepaymentAmount(beforeRepaymentAmount)
                .afterRepaymentAmount(request.getRepaymentAmount())
                .balance(updatedBalance.getBalance())
                .createdAt(repayment.getCreatedAt())
                .updatedAt(repayment.getUpdatedAt())
                .build();
    }

    @Override
    public void delete(Long repaymentId) {
        Repayment repayment = repaymentRepository.findById(repaymentId).orElseThrow(() -> {
            throw new BaseException(ResultType.SYSTEM_ERROR);
        });

        Long applicationId = repayment.getApplicationId();
        BigDecimal removeRepaymentAmount = repayment.getRepaymentAmount();

        balanceService.repaymentUpdate(applicationId,
                BalanceDTO.RepaymentRequest.builder()
                        .repaymentAmount(removeRepaymentAmount)
                        .type(RepaymentType.ADD)
                        .build());

        repayment.setIsDeleted(true);
        repaymentRepository.save(repayment);
    }

    private boolean isRepayableApplication(Long applicationId) {
        Optional<Application> existedApplication = applicationRepository.findById(applicationId);
        if (existedApplication.isEmpty()) {
            return false;
        }

        if (existedApplication.get().getContractedAt() == null) {
            return false;
        }

        Optional<Entry> existedEntry = entryRepository.findByApplicationId(applicationId);
        return existedEntry.isPresent();
    }
}
