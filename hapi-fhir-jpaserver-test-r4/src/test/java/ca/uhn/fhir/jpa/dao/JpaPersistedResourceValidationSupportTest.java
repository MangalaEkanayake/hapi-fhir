
package ca.uhn.fhir.jpa.dao;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.term.api.ITermReadSvc;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.param.UriParam;
import ca.uhn.fhir.sl.cache.Cache;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.StructureDefinition;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Function;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hl7.fhir.common.hapi.validation.support.ValidationConstants.LOINC_LOW;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JpaPersistedResourceValidationSupportTest {

	private FhirContext theFhirContext = FhirContext.forR4();

	@Mock private ITermReadSvc myTermReadSvc;
	@Mock private DaoRegistry myDaoRegistry;
	@Mock private Cache<String, IBaseResource> myLoadCache;
	@Mock private IFhirResourceDao<ValueSet> myValueSetResourceDao;

	@InjectMocks
	private IValidationSupport testedClass =
		new JpaPersistedResourceValidationSupport(theFhirContext);

	private Class<? extends IBaseResource> myCodeSystemType = CodeSystem.class;
	private Class<? extends IBaseResource> myValueSetType = ValueSet.class;


	@BeforeEach
	public void setup() {
		ReflectionTestUtils.setField(testedClass, "myValueSetType", myValueSetType);
	}


	@Nested
	public class FetchCodeSystemTests {

		@Test
		void fetchCodeSystemMustUseForcedId() {
			testedClass.fetchCodeSystem("string-containing-loinc");

			verify(myTermReadSvc, times(1)).readCodeSystemByForcedId(LOINC_LOW);
			verify(myLoadCache, never()).get(anyString(), isA(Function.class));
		}


		@Test
		void fetchCodeSystemMustNotUseForcedId() {
			testedClass.fetchCodeSystem("string-not-containing-l-o-i-n-c");

			verify(myTermReadSvc, never()).readCodeSystemByForcedId(LOINC_LOW);
			verify(myLoadCache, times(1)).get(anyString(), isA(Function.class));
		}

	}


	@Nested
	public class FetchValueSetTests {

		@Test
		void fetchValueSetMustUseForcedId() {
			final String valueSetId = "string-containing-loinc";
			assertNull(testedClass.fetchValueSet(valueSetId));
		}


		@Test
		void fetchValueSetMustNotUseForcedId() {
			testedClass.fetchValueSet("string-not-containing-l-o-i-n-c");

			verify(myLoadCache, times(1)).get(anyString(), isA(Function.class));
		}

	}

	@Nested
	class FetchStructureDefinitionTests {

		@Mock
		private DaoRegistry myDaoRegistry;

		@InjectMocks
		private final JpaPersistedResourceValidationSupport testClass = new JpaPersistedResourceValidationSupport(theFhirContext);

		@Captor
		ArgumentCaptor<SearchParameterMap> searchParameterMapCaptor;
		@Test
		@DisplayName("fetch StructureDefinition by version less url")
		void fetchStructureDefinitionForUrl() {
			final String profileUrl = "http://example.com/fhir/StructureDefinition/exampleProfile";
			IFhirResourceDao mockDao = mock(IFhirResourceDao.class);
			when(mockDao.search(any())).thenReturn(mock(IBundleProvider.class));
			when(myDaoRegistry.getResourceDao(anyString())).thenReturn(mockDao);

			testClass.fetchResource(StructureDefinition.class, profileUrl);

			verify(mockDao).search(searchParameterMapCaptor.capture());
			SearchParameterMap searchParams = searchParameterMapCaptor.getValue();
			String uriParam = searchParams.get(StructureDefinition.SP_URL)
				.get(0)
				.stream()
				.map(UriParam.class::cast)
				.map(UriParam::getValue)
				.findFirst()
				.orElse(null);
			assertThat(uriParam).isEqualTo(profileUrl);
		}

		@Test
		@DisplayName("fetch StructureDefinition by versioned url")
		void fetchStructureDefinitionForVersionedUrl() {
			final String profileUrl = "http://example.com/fhir/StructureDefinition/exampleProfile|1.1.0";
			IFhirResourceDao mockDao = mock(IFhirResourceDao.class);
			when(mockDao.search(any())).thenReturn(mock(IBundleProvider.class));
			when(myDaoRegistry.getResourceDao(anyString())).thenReturn(mockDao);

			testClass.fetchResource(StructureDefinition.class, profileUrl);

			verify(mockDao).search(searchParameterMapCaptor.capture());
			SearchParameterMap searchParams = searchParameterMapCaptor.getValue();
			String uriParam = searchParams.get(StructureDefinition.SP_URL)
				.get(0)
				.stream()
				.map(UriParam.class::cast)
				.map(UriParam::getValue)
				.findFirst()
				.orElse(null);
			assertThat(uriParam).isEqualTo("http://example.com/fhir/StructureDefinition/exampleProfile");

			String versionParam = searchParams.get(StructureDefinition.SP_VERSION)
				.get(0)
				.stream()
				.map(TokenParam.class::cast)
				.map(TokenParam::getValue)
				.findFirst()
				.orElse(null);
			assertThat(versionParam).isEqualTo("1.1.0");
		}
	}
}
