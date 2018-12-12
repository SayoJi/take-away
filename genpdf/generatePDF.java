package com.pru.hk.client.life.spring.bean;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.itextpdf.text.pdf.codec.Base64;
import com.lowagie.text.DocumentException;
import com.pru.hk.client.life.eai.dao.ChangeNameEaiDao;
import com.pru.hk.client.life.to.ChangeNameClient;
import com.pru.hk.client.life.to.ChangeNameRequest;
import com.pru.hk.client.life.to.ChangeNameSubmissionInfo;
import com.pru.hk.common.dao.BaseEaiDao;
import com.pru.hk.common.dao.ReferenceNumberDao;
import com.pru.hk.common.spring.bean.SesvCommonBusinessBean;
import com.pru.hk.policy.life.dao.DAOException;
import com.pru.hk.policy.life.spring.bean.BusinessException;
import com.pru.hk.policy.life.spring.bean.DocumentBusinessBean;
import com.pru.hk.to.policy.life.DocumentTO;
import com.pru.hk.util.PRUConstant;
import com.pru.hk.util.XhtmlToPdfConvertor;
import com.pru.hk.util.velocity.VelocityUtil;

@Component
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class ChangeNameBusinessBean extends BaseEaiDao {
	private static final Logger logger = Logger
			.getLogger(ChangeNameBusinessBean.class);
	private static final String UPOP = "UPOP";
	private static final String PDF_TEMPLATE = "change_name_template.vm";
	private static final String PDF_CUSTOME_FONT_SETTINGS = "\n@font-face { font-family: Arial Unicode MS; "
			+ "src: local(\"%s\"); -fs-pdf-font-encoding: Identity-H; }\n\n"
			+ "* { font-family: Arial Unicode MS; }";
	private static final DateFormat df = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss");

	@Value("#{aesProperties['ECLAIMS_PDF_FONTPATH']}")
	private String PDF_FONT_PATH;

	@Autowired
	private SesvCommonBusinessBean sesvCommonBusinessBean;

	@Autowired
	private ReferenceNumberDao referenceNumberDao;

	@Autowired
	private ChangeNameEaiDao changeNameEaiDao;

	@Autowired
	private VelocityUtil velocityUtil;

	@Autowired
	private MessageSource messageSource;

	@Autowired
	private DocumentBusinessBean documentBusinessBean;

	@Autowired
	private ClientBusinessBean clientBusinessBean;

	@Value("#{aesProperties['ECLAIMS_COMPANY_LOGO_PATH']}")
	private String COMPANY_LOGO_PATH;

	private class ChangeNameClientComparator implements
			Comparator<ChangeNameClient> {
		public int compare(ChangeNameClient c1, ChangeNameClient c2) {
			if (c1 != null && c2 != null) {
				int rs = c1.getClientCode().compareTo(c2.getClientCode());

				if (rs != 0) {
					return rs;
				}

				rs = c1.getPolicyNo().compareTo(c2.getPolicyNo());

				if (rs != 0) {
					return rs;
				}

				if (!c1.getClientRole().equals(c2.getClientRole())) {
					// policy owner > life assured
					if ("O".equals(c1.getClientCode())) {
						return -1;
					} else {
						return 1;
					}
				}
			}

			return 0;
		}
	}

	@Transactional(readOnly = true)
	public List<ChangeNameClient> getClientList(String clientCode)
			throws BusinessException {
		logger.info("getClientList - start: clientCode=" + clientCode);

		try {
			List<ChangeNameClient> result = changeNameEaiDao
					.getClientList(clientCode);
			List<ChangeNameClient> filteredResult = new ArrayList<ChangeNameClient>();

			for (ChangeNameClient client : result) {
				if ("0000".equals(client.getStatusCode())) {
					// remove record which cannot pass the pre-checking
					filteredResult.add(client);
				}
			}

			if (!filteredResult.isEmpty()) {
				result = filteredResult;
			}

			return result;
		} catch (Exception e) {
			logger.error("getClientList - error: clientCode=" + clientCode, e);
			throw new BusinessException("getClientList", e.getMessage());
		}
	}

	@Transactional(value = "transactionManagerCwsDB2", propagation = Propagation.REQUIRES_NEW, readOnly = false, rollbackFor = Exception.class)
	public String generateNewMyPruRefNo(Calendar cal) throws DAOException {
		return referenceNumberDao.getReferenceNumber("", cal, UPOP);
	}

	@Transactional(value = "transactionManagerCwsDB2", readOnly = false, rollbackFor = Exception.class)
	public ChangeNameSubmissionInfo submitChangeNameRequest(
			String requestorClientCode, List<ChangeNameClient> clients,
			String lang, boolean optOut, String mobileNo)
			throws BusinessException {
		logger.info("submitChangeNameRequest - start: clientCode="
				+ requestorClientCode);
		try {

			ChangeNameSubmissionInfo submitInfo = new ChangeNameSubmissionInfo();
			submitInfo.setSubmitDate(Calendar.getInstance());
			submitInfo.setMyPrudentialRefNo(generateNewMyPruRefNo(submitInfo
					.getSubmitDate()));

			// pre-allocate workflow batch seq
			List<String> batchSeqList = sesvCommonBusinessBean
					.allocateDailySeqNum(new Date(), clients.size());
			logger.info("myPrudential reference no="
					+ submitInfo.getMyPrudentialRefNo());

			int i = 0;
			Set<String> submittedClientCode = new HashSet<String>();
			byte[] pdf = generatePdf(lang, optOut, mobileNo, submitInfo,
					clients);

			for (ChangeNameClient client : clients) {
				// generate PDF online form and submit to CM
				String changeNo = changeNameEaiDao.sendFilesToCM(
						submitInfo.getMyPrudentialRefNo(), batchSeqList.get(i),
						submitInfo.getSubmitDate(), client, pdf);

				if (submittedClientCode.add(client.getClientCode())) {
					// submit to prulife and only do once per client code
					changeNameEaiDao.submitChangeNameRequest(
							submitInfo.getMyPrudentialRefNo(),
							submitInfo.getSubmitDate(), requestorClientCode,
							client);

					// update opt out status
					clientBusinessBean.updateOptOut(client.getClientCode(),
							optOut);
				}

				// second submission to prulife to pass the policy no
				changeNameEaiDao.submitChangeNameRequestByPolicy(
						submitInfo.getMyPrudentialRefNo(), changeNo, client);
				i++;
			}

			changeNameEaiDao.finalizeChangeNameRequestSubmission(submitInfo
					.getMyPrudentialRefNo());
			logger.info("submitChangeNameRequest - end: clientCode="
					+ requestorClientCode);
			return submitInfo;
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			logger.error("submitChangeNameRequest - error: clientCode="
					+ requestorClientCode, e);
			throw new BusinessException("submitChangeNameRequest",
					e.getMessage());
		}
	}

	@Transactional(readOnly = true)
	public List<ChangeNameRequest> getChangeNameRequestProgress(
			String clientCode) throws BusinessException {
		logger.info("getChangeNameRequestProgress - start: clientCode="
				+ clientCode);

		try {
			List<ChangeNameRequest> result = changeNameEaiDao
					.getChangeNameRequestProgress(clientCode);
			logger.info("getChangeNameRequestProgress - end: clientCode="
					+ clientCode);
			return result;
		} catch (Exception e) {
			logger.error("getChangeNameRequestProgress - error: clientCode="
					+ clientCode, e);
			throw new BusinessException("getChangeNameRequestProgress",
					e.getMessage());
		}
	}
	
	@Transactional(readOnly = true)
	public String getDocumentId(String policyNo, String myPruRefNo,
			String formId) throws BusinessException {
		logger.info("getDocumentId - start: policyNo=" + policyNo
				+ ", myPruRefNo=" + myPruRefNo + ", formId=" + formId);

		try {
			return changeNameEaiDao.getChangeNameDocumentId(policyNo,
					myPruRefNo, formId);
		} catch (Exception e) {
			logger.error("getDocumentId - error: policyNo=" + policyNo
					+ ", myPruRefNo=" + myPruRefNo + ", formId=" + formId, e);
			throw new BusinessException("retrieveChangeNameDocument",
					e.getMessage());
		} finally {
			logger.info("getDocumentId - end: policyNo=" + policyNo
					+ ", myPruRefNo=" + myPruRefNo + ", formId=" + formId);
		}
	}

	@Transactional(readOnly = true)
	public byte[] retrieveDocument(String docId, String formId) throws BusinessException {
		logger.info("retrieveDocument - start: docId=" + docId);

		try {
			DocumentTO docTo = documentBusinessBean
					.retrieveDocument(docId, formId);
			return Base64.decode(docTo.getDocObj());
		} catch (Exception e) {
			logger.error("getDocumentId - error: docId=" + docId);
			throw new BusinessException("retrieveDocument - error: docId="
					+ docId, e.getMessage());
		} finally {
			logger.info("retrieveDocument - end: docId=" + docId);
		}
	}

	private byte[] generatePdf(String lang, boolean optOut, String mobileNo,
			ChangeNameSubmissionInfo submitInfo, List<ChangeNameClient> clients)
			throws DocumentException, IOException {

		logger.info("generatePdf start: lang=" + lang + ", optOut=" + optOut
				+ ", mobileNo=" + mobileNo);

		// client object list sorting
		Collections.sort(clients, new ChangeNameClientComparator());

		// create client code & client list map
		Map<String, List<ChangeNameClient>> clientMap = new TreeMap<String, List<ChangeNameClient>>();

		for (ChangeNameClient client : clients) {
			List<ChangeNameClient> clientList = clientMap.get(client
					.getClientCode());

			if (clientList == null) {
				clientList = new ArrayList<ChangeNameClient>();
				clientMap.put(client.getClientCode(), clientList);
			}

			clientList.add(client);
		}

		// construct data map for pdf generation
		Map<String, Object> dataMap = new HashMap<String, Object>();
		dataMap.put("messageSource", messageSource);

		if (PRUConstant.ZH.equals(lang)) {
			dataMap.put("locale", Locale.CHINESE);
		} else {
			dataMap.put("locale", Locale.ENGLISH);
		}

		dataMap.put("companyLogoPath", COMPANY_LOGO_PATH);
		dataMap.put("submitInfo", submitInfo);
		dataMap.put("clientMap", clientMap);
		dataMap.put("optOut", new Boolean(optOut));
		dataMap.put("mobileNo", mobileNo);
		dataMap.put("submitTime",
				df.format(submitInfo.getSubmitDate().getTime()));

		// start processing template
		String result = velocityUtil.processTemplate(PDF_TEMPLATE, dataMap,
				PRUConstant.ENC_UTF8);
		XhtmlToPdfConvertor xhtmlToPdfConvertor = new XhtmlToPdfConvertor(
				PDF_FONT_PATH);
		ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
		xhtmlToPdfConvertor.convert(
				new ByteArrayInputStream(result.getBytes()),
				byteArrayOutputStream, PDF_CUSTOME_FONT_SETTINGS);
		byte[] pdf = byteArrayOutputStream.toByteArray();

		if (logger.isDebugEnabled()) {
			// debug logic to output pdf to temp folder
			OutputStream out = new FileOutputStream("/tmp/change_name.pdf");

			try {
				out.write(pdf);
			} finally {
				out.close();
			}
		}

		logger.info("generatePdf end: lang=" + lang + ", optOut=" + optOut
				+ ", mobileNo=" + mobileNo);
		return pdf;
	}
}