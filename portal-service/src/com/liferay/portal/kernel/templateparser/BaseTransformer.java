/**
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.kernel.templateparser;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.InstanceFactory;
import com.liferay.portal.kernel.util.LocalizationUtil;
import com.liferay.portal.kernel.util.PortalClassLoaderUtil;
import com.liferay.portal.kernel.util.PropertiesUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.theme.ThemeDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Brian Wing Shun Chan
 * @author Raymond Augé
 * @author Wesley Gong
 * @author Angelo Jefferson
 * @author Hugo Huijser
 * @author Marcellus Tavares
 * @author Juan Fernández
 */
public abstract class BaseTransformer implements Transformer {

	public String transform(
			ThemeDisplay themeDisplay, Map<String, Object> contextObjects,
			String script, String langType)
		throws Exception {

		if (Validator.isNull(langType)) {
			return null;
		}

		String templateParserClassName = getTemplateParserClassName(langType);

		if (Validator.isNull(templateParserClassName)) {
			return null;
		}

		if (_log.isDebugEnabled()) {
			_log.debug("Template parser class name " + templateParserClassName);
		}

		TemplateParser templateParser = null;

		try {
			templateParser = (TemplateParser)InstanceFactory.newInstance(
				PortalClassLoaderUtil.getClassLoader(),
				templateParserClassName);
		}
		catch (Exception e) {
			throw new TransformException(e);
		}

		templateParser.setContextObjects(contextObjects);
		templateParser.setScript(script);
		templateParser.setThemeDisplay(themeDisplay);

		return templateParser.transform();
	}

	public String transform(
			ThemeDisplay themeDisplay, Map<String, String> tokens,
			String viewMode, String languageId, String xml, String script,
			String langType)
		throws Exception {

		// Setup listeners

		if (_log.isDebugEnabled()) {
			_log.debug("Language " + languageId);
		}

		if (Validator.isNull(viewMode)) {
			viewMode = Constants.VIEW;
		}

		if (_logTokens.isDebugEnabled()) {
			String tokensString = PropertiesUtil.list(tokens);

			_logTokens.debug(tokensString);
		}

		if (_logTransformBefore.isDebugEnabled()) {
			_logTransformBefore.debug(xml);
		}

		List<TransformerListener> transformerListeners =
			new ArrayList<TransformerListener>();

		String[] transformerListenersClassNames =
			getTransformerListenersClassNames();

		for (String transformerListenersClassName :
				transformerListenersClassNames) {

			TransformerListener transformerListener = null;

			try {
				if (_log.isDebugEnabled()) {
					_log.debug(
						"Instantiate listener " +
							transformerListenersClassName);
				}

				ClassLoader classLoader =
					PortalClassLoaderUtil.getClassLoader();

				transformerListener =
					(TransformerListener)InstanceFactory.newInstance(
						classLoader, transformerListenersClassName);

				transformerListeners.add(transformerListener);
			}
			catch (Exception e) {
				_log.error(e, e);
			}

			// Modify XML

			if (_logXmlBeforeListener.isDebugEnabled()) {
				_logXmlBeforeListener.debug(xml);
			}

			if (transformerListener != null) {
				xml = transformerListener.onXml(xml, languageId, tokens);

				if (_logXmlAfterListener.isDebugEnabled()) {
					_logXmlAfterListener.debug(xml);
				}
			}

			// Modify script

			if (_logScriptBeforeListener.isDebugEnabled()) {
				_logScriptBeforeListener.debug(script);
			}

			if (transformerListener != null) {
				script = transformerListener.onScript(
					script, xml, languageId, tokens);

				if (_logScriptAfterListener.isDebugEnabled()) {
					_logScriptAfterListener.debug(script);
				}
			}
		}

		// Transform

		String output = null;

		if (Validator.isNull(langType)) {
			output = LocalizationUtil.getLocalization(xml, languageId);
		}
		else {
			String templateParserClassName = getTemplateParserClassName(
				langType);

			if (_log.isDebugEnabled()) {
				_log.debug(
					"Template parser class name " + templateParserClassName);
			}

			if (Validator.isNotNull(templateParserClassName)) {
				TemplateParser templateParser = null;

				try {
					templateParser =
						(TemplateParser)InstanceFactory.newInstance(
							PortalClassLoaderUtil.getClassLoader(),
							templateParserClassName);
				}
				catch (Exception e) {
					throw new TransformException(e);
				}

				templateParser.setLanguageId(languageId);
				templateParser.setScript(script);
				templateParser.setThemeDisplay(themeDisplay);
				templateParser.setTokens(tokens);
				templateParser.setViewMode(viewMode);
				templateParser.setXML(xml);

				output = templateParser.transform();
			}
		}

		// Postprocess output

		for (TransformerListener transformerListener : transformerListeners) {

			// Modify output

			if (_logOutputBeforeListener.isDebugEnabled()) {
				_logOutputBeforeListener.debug(output);
			}

			output = transformerListener.onOutput(output, languageId, tokens);

			if (_logOutputAfterListener.isDebugEnabled()) {
				_logOutputAfterListener.debug(output);
			}
		}

		if (_logTransfromAfter.isDebugEnabled()) {
			_logTransfromAfter.debug(output);
		}

		return output;
	}

	protected abstract String getTemplateParserClassName(String langType);

	protected abstract String[] getTransformerListenersClassNames();

	private static Log _log = LogFactoryUtil.getLog(BaseTransformer.class);

	private static Log _logOutputAfterListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".OutputAfterListener");
	private static Log _logOutputBeforeListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".OutputBeforeListener");
	private static Log _logScriptAfterListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".ScriptAfterListener");
	private static Log _logScriptBeforeListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".ScriptBeforeListener");
	private static Log _logTokens = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".Tokens");
	private static Log _logTransformBefore = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".TransformBefore");
	private static Log _logTransfromAfter = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".TransformAfter");
	private static Log _logXmlAfterListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".XmlAfterListener");
	private static Log _logXmlBeforeListener = LogFactoryUtil.getLog(
		BaseTransformer.class.getName() + ".XmlBeforeListener");

}