/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.fhir2.api.translators.impl;

import static org.apache.commons.lang3.Validate.notNull;

import javax.validation.constraints.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.lang.Validate;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Practitioner;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Provider;
import org.openmrs.ProviderAttribute;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirGlobalPropertyService;
import org.openmrs.module.fhir2.api.dao.FhirPractitionerDao;
import org.openmrs.module.fhir2.api.translators.GenderTranslator;
import org.openmrs.module.fhir2.api.translators.PersonAddressTranslator;
import org.openmrs.module.fhir2.api.translators.PersonNameTranslator;
import org.openmrs.module.fhir2.api.translators.PractitionerTranslator;
import org.openmrs.module.fhir2.api.translators.ProvenanceTranslator;
import org.openmrs.module.fhir2.api.translators.TelecomTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class PractitionerTranslatorProviderImpl implements PractitionerTranslator<Provider> {
	
	@Autowired
	private PersonNameTranslator nameTranslator;
	
	@Autowired
	private PersonAddressTranslator addressTranslator;
	
	@Autowired
	private GenderTranslator genderTranslator;
	
	@Autowired
	private TelecomTranslator<BaseOpenmrsData> telecomTranslator;
	
	@Autowired
	private FhirPractitionerDao fhirPractitionerDao;
	
	@Autowired
	private FhirGlobalPropertyService globalPropertyService;
	
	@Autowired
	private ProvenanceTranslator<Provider> provenanceTranslator;
	
	@Override
	public Provider toOpenmrsType(Provider existingProvider, Practitioner practitioner) {
		notNull(existingProvider, "The existing Provider object should not be null");
		notNull(practitioner, "The Practitioner object should not be null");
		
		existingProvider.setUuid(practitioner.getId());
		
		Validate.notEmpty(practitioner.getIdentifier(), "Practitioner Identifier cannot be empty");
		existingProvider.setIdentifier(practitioner.getIdentifier().get(0).getValue());
		
		practitioner.getTelecom().stream().map(
		    contactPoint -> (ProviderAttribute) telecomTranslator.toOpenmrsType(new ProviderAttribute(), contactPoint))
		        .filter(Objects::nonNull).forEach(existingProvider::addAttribute);
		
		return existingProvider;
	}
	
	@Override
	public Practitioner toFhirResource(Provider provider) {
		notNull(provider, "The Provider object should not be null");
		
		Practitioner practitioner = new Practitioner();
		Identifier identifier = new Identifier();
		identifier.setSystem(FhirConstants.OPENMRS_FHIR_EXT_PROVIDER_IDENTIFIER);
		identifier.setValue(provider.getIdentifier());
		practitioner.addIdentifier(identifier);
		
		practitioner.setId(provider.getUuid());
		practitioner.setActive(provider.getRetired());
		practitioner.setTelecom(getProviderContactDetails(provider));
		
		if (provider.getPerson() != null) {
			practitioner.setBirthDate(provider.getPerson().getBirthdate());
			practitioner.setGender(genderTranslator.toFhirResource(provider.getPerson().getGender()));
			
			for (PersonName name : provider.getPerson().getNames()) {
				practitioner.addName(nameTranslator.toFhirResource(name));
			}
			
			for (PersonAddress address : provider.getPerson().getAddresses()) {
				practitioner.addAddress(addressTranslator.toFhirResource(address));
			}
		}
		
		practitioner.getMeta().setLastUpdated(provider.getDateChanged());
		practitioner.addContained(provenanceTranslator.getCreateProvenance(provider));
		practitioner.addContained(provenanceTranslator.getUpdateProvenance(provider));
		
		return practitioner;
	}
	
	public List<ContactPoint> getProviderContactDetails(@NotNull Provider provider) {
		return fhirPractitionerDao
		        .getActiveAttributesByPractitionerAndAttributeTypeUuid(provider,
		            globalPropertyService.getGlobalProperty(FhirConstants.PROVIDER_CONTACT_ATTRIBUTE_TYPE))
		        .stream().map(telecomTranslator::toFhirResource).collect(Collectors.toList());
	}
	
	@Override
	public Provider toOpenmrsType(Practitioner practitioner) {
		notNull(practitioner, "The Practitioner object should not be null");
		return toOpenmrsType(new org.openmrs.Provider(), practitioner);
	}
	
}
