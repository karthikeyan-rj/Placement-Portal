package com.college.placement.company;

import com.college.placement.audit.AuditService;
import com.college.placement.common.enums.CompanyType;
import com.college.placement.common.enums.Role;
import com.college.placement.common.exception.BadRequestException;
import com.college.placement.common.exception.ConflictException;
import com.college.placement.common.exception.ResourceNotFoundException;
import com.college.placement.company.dto.CompanyResponse;
import com.college.placement.company.dto.CreateCompanyRequest;
import com.college.placement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final AuditService auditService;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public Page<CompanyResponse> searchCompanies(String search, Pageable pageable) {
        return companyRepository.searchCompanies(search, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getCompanyById(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        securityUtils.requireRole(Role.PO);

        if (companyRepository.existsByNameIgnoreCase(request.getName())) {
            throw new ConflictException("Company already exists: " + request.getName());
        }

        CompanyType companyType = null;
        if (request.getCompanyType() != null) {
            try {
                companyType = CompanyType.valueOf(request.getCompanyType().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid company type: " + request.getCompanyType());
            }
        }

        Company company = Company.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .companyType(companyType)
                .website(request.getWebsite())
                .active(true)
                .build();

        company = companyRepository.save(company);
        auditService.log("CREATE_COMPANY", "Company", company.getId(), company.getName());

        return toResponse(company);
    }

    @Transactional
    public CompanyResponse updateCompany(Long id, CreateCompanyRequest request) {
        securityUtils.requireRole(Role.PO);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));

        if (request.getName() != null) company.setName(request.getName().trim());
        if (request.getDescription() != null) company.setDescription(request.getDescription());
        if (request.getWebsite() != null) company.setWebsite(request.getWebsite());
        if (request.getCompanyType() != null) {
            try {
                company.setCompanyType(CompanyType.valueOf(request.getCompanyType().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid company type: " + request.getCompanyType());
            }
        }

        company = companyRepository.save(company);
        auditService.log("UPDATE_COMPANY", "Company", company.getId(), company.getName());

        return toResponse(company);
    }

    @Transactional
    public void deactivateCompany(Long id) {
        securityUtils.requireRole(Role.PO);

        Company company = companyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Company", id));
        company.setActive(false);
        companyRepository.save(company);

        auditService.log("DEACTIVATE_COMPANY", "Company", id, company.getName());
    }

    private CompanyResponse toResponse(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .description(company.getDescription())
                .companyType(company.getCompanyType() != null ? company.getCompanyType().name() : null)
                .website(company.getWebsite())
                .active(company.getActive())
                .build();
    }
}
