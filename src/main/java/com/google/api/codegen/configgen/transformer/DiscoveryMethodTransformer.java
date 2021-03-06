/* Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.api.codegen.configgen.transformer;

import com.google.api.codegen.ResourceNameTreatment;
import com.google.api.codegen.config.DiscoveryField;
import com.google.api.codegen.config.DiscoveryMethodModel;
import com.google.api.codegen.config.FieldModel;
import com.google.api.codegen.config.MethodModel;
import com.google.api.codegen.configgen.HttpPagingParameters;
import com.google.api.codegen.configgen.PagingParameters;
import com.google.api.codegen.configgen.viewmodel.PageStreamingResponseView;
import com.google.api.codegen.discovery.Schema;
import com.google.api.codegen.util.Name;
import com.google.common.base.Strings;
import javax.annotation.Nullable;

/** Discovery-doc-specific functions for transforming method models into views for configgen. */
public class DiscoveryMethodTransformer implements InputSpecificMethodTransformer {
  private static final PagingParameters PAGING_PARAMETERS = new HttpPagingParameters();

  // For Discovery doc configgen, assume that paged resource field name is "items". This is the only resource name
  // seen in Google Cloud Compute API.
  private static String PAGING_RESOURCE_FIELD_NAME = "items";

  @Override
  public PagingParameters getPagingParameters() {
    return PAGING_PARAMETERS;
  }

  @Override
  public ResourceNameTreatment getResourceNameTreatment(MethodModel methodModel) {
    return ResourceNameTreatment.STATIC_TYPES;
  }

  @Override
  public boolean isIgnoredParameter(FieldModel parameter) {
    Schema f = ((DiscoveryField) parameter).getDiscoveryField();
    if (parameter.isRequired() || f.isPathParam()) {
      return false;
    }
    if (PAGING_PARAMETERS.getIgnoredParameters().contains(parameter.getSimpleName())) {
      return true;
    }
    return !Strings.isNullOrEmpty(f.description())
        && f.description().toLowerCase().contains("optional");
  }

  /**
   * Returns a non-null PageStreamingResponseView iff the method has a response object that contains
   * a nextPageToken child property.
   */
  @Nullable
  @Override
  public PageStreamingResponseView generatePageStreamingResponse(MethodModel methodModel) {
    DiscoveryMethodModel method = (DiscoveryMethodModel) methodModel;
    String resourcesName = null;
    boolean hasNextPageToken = false;

    // Find the paged resource object from inside the response object.
    for (DiscoveryField field : method.getOutputFields()) {
      if (field.getDiscoveryField().properties() == null) {
        continue;
      }
      // Verify that the paging response object contains a paging token.
      for (Schema property : field.getDiscoveryField().properties().values()) {
        if (property.getIdentifier().equals(PAGING_PARAMETERS.getNameForNextPageToken())) {
          hasNextPageToken = true;
          break;
        }
      }

      Schema itemCollectionSchema =
          field.getDiscoveryField().properties().get(PAGING_RESOURCE_FIELD_NAME);
      if (itemCollectionSchema == null) {
        continue;
      }
      resourcesName = PAGING_RESOURCE_FIELD_NAME;
    }

    if (!hasNextPageToken) {
      return null;
    }

    // In the resulting gapic config, the resources_object must be a non-null value for the yaml to be parseable.
    String configResourcesName;
    if (resourcesName == null) {
      configResourcesName = MethodTransformer.TODO_STRING;
    } else {
      configResourcesName = Name.anyCamel(resourcesName).toLowerCamel();
    }

    return PageStreamingResponseView.newBuilder()
        .tokenField(PAGING_PARAMETERS.getNameForNextPageToken())
        .resourcesField(configResourcesName)
        .build();
  }
}
