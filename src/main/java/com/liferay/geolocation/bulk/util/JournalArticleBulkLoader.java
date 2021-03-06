package com.liferay.geolocation.bulk.util;

import com.liferay.dev.search.data.boston.index.Boston311IndexDefinition;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMStructure;
import com.liferay.dynamic.data.mapping.model.DDMTemplate;
import com.liferay.geolocation.bulk.ddm.DDMFormFactory;
import com.liferay.geolocation.bulk.ddm.DDMStructureFactory;
import com.liferay.geolocation.bulk.ddm.DDMTemplateFactory;
import com.liferay.geolocation.bulk.servicecontext.ServiceContextHelper;
import com.liferay.journal.model.JournalArticle;
import com.liferay.journal.model.JournalArticleConstants;
import com.liferay.journal.model.JournalFolderConstants;
import com.liferay.journal.service.JournalArticleLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.model.BaseModel;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.util.LocaleUtil;
import com.liferay.portal.search.document.Document;
import com.liferay.portal.search.document.DocumentBuilderFactory;
import com.liferay.portal.search.engine.adapter.SearchEngineAdapter;
import com.liferay.portal.search.engine.adapter.document.IndexDocumentRequest;
import com.liferay.portal.search.geolocation.GeoBuilders;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = JournalArticleBulkLoader.class)
public class JournalArticleBulkLoader {

	private Request311ToSearchDocumentTranslator translatorSearch;

	public void load() throws Exception {
		init();

		AtomicInteger count = new AtomicInteger();

		Consumer<TitleContent> counter = x -> {
			System.out.println("" + count.incrementAndGet() + "/" + limit);
			System.out.println(x.title);
			System.out.println(x.xml);
		};

		this.translatorSearch =
			new Request311ToSearchDocumentTranslator(documentBuilderFactory, geoBuilders);

		dataset.entries().limit(limit).map(this::toTitleContent).forEach(
			counter.andThen(this::addArticle));

		System.out.println(
				"***********************************************\n"+
				" Geolocation demo dataset loaded successfully. \n"+
				"***********************************************\n"
			);
	}

	public void setDataset(GeolocationDemoDataset dataset) {
		this.dataset = dataset;
	}

	public void setLimit(int limit) {
		this.limit = limit;
	}

	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

	private void init() throws Exception {
		serviceContextHelper.init();

		createDDMFormStructureAndTemplate();
	}

	static class TitleContent {

		String title;
		String xml;
		Document doc;
	}

	private TitleContent toTitleContent(Request311 entry) {
		return new TitleContent() {

			{
				title = entry.case_title;
				xml = translator.translate(entry);
				doc = translatorSearch.translate(entry);
			}
		};
	}

	private void addArticle(TitleContent titleContent) {
		if (dryRun) {
			return;
		}

		indexDocument(titleContent.doc);

		try {
			addArticle(titleContent.title, titleContent.xml);
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void indexDocument(Document doc) {
		IndexDocumentRequest indexDocumentRequest = new IndexDocumentRequest(
			Boston311IndexDefinition.INDEX_NAME, doc);

		indexDocumentRequest.setType(Boston311IndexDefinition.TYPE_NAME);

		searchEngineAdapter.execute(indexDocumentRequest);
	}

	private void addArticle(String title, String xml) throws Exception {
		ServiceContext serviceContext1 =
			serviceContextHelper.getServiceContextScopeGroup();

		Map<Locale, String> titleMap = new HashMap<>();

		titleMap.put(LocaleUtil.getSiteDefault(), title);

		journalArticleLocalService.addArticle(
			serviceContext1.getUserId(), serviceContext1.getScopeGroupId(),
			JournalFolderConstants.DEFAULT_PARENT_FOLDER_ID,
			JournalArticleConstants.CLASSNAME_ID_DEFAULT,
			0, StringPool.BLANK, true, 0, titleMap,
			null, xml, ddmStructureKey, ddmTemplateKey,
			null, 1, 1, 1965, 0, 0,
			0, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, true, true, false, null, null,
			null, null, serviceContext1);
	}

	private void createDDMFormStructureAndTemplate() throws Exception {
		long userId = serviceContextHelper.getUserId();

		long scopeGroupId = serviceContextHelper.getScopeGroupId();

		String resourceClassName = JournalArticle.class.getName();

		DDMForm ddmForm = ddmFormFactory.createDDMForm();

		DDMStructure ddmStructure =
			ddmStructureFactory.createDDMStructure(
				userId, scopeGroupId, resourceClassName, ddmForm,
				serviceContextHelper.getServiceContextGuest());

		DDMTemplate ddmTemplate =
			ddmTemplateFactory.createTemplate(
				userId, scopeGroupId, ddmStructure.getStructureId(),
				resourceClassName);

		ddmStructureKey = ddmStructure.getStructureKey();

		ddmTemplateKey = ddmTemplate.getTemplateKey();
	}

	protected BaseModel<?> _baseModel;

	private GeolocationDemoDataset dataset;
	private String ddmStructureKey;
	private String ddmTemplateKey;
	private boolean dryRun;
	private int limit;
	private final Request311ToJournalArticleXMLContentTranslator translator =
		new Request311ToJournalArticleXMLContentTranslator();

	@Reference
	protected DocumentBuilderFactory documentBuilderFactory;

	@Reference
	protected GeoBuilders geoBuilders;

	@Reference
	protected JournalArticleLocalService journalArticleLocalService;

	@Reference
	protected ServiceContextHelper serviceContextHelper;

	@Reference
	protected DDMFormFactory ddmFormFactory;

	@Reference
	protected DDMStructureFactory ddmStructureFactory;

	@Reference
	protected DDMTemplateFactory ddmTemplateFactory;

	@Reference
	protected SearchEngineAdapter searchEngineAdapter;

}