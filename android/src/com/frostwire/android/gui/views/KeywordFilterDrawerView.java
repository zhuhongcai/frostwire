/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml),
 *            Marcelina Knitter (@marcelinkaaa)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.android.gui.views;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.util.UIUtils;
import com.frostwire.search.KeywordDetector;
import com.frostwire.search.KeywordFilter;
import com.frostwire.util.Logger;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

/**
 * @author aldenml
 * @author gubatron
 * @author marcelinkaaa
 */
public final class KeywordFilterDrawerView extends LinearLayout implements KeywordTagView.KeywordTagViewListener {

    private static Logger LOG = Logger.getLogger(KeywordFilterDrawerView.class);

    private KeywordFiltersPipelineListener pipelineListener;
    private EnumMap<KeywordDetector.Feature, TagsController> featureContainer = new EnumMap<>(KeywordDetector.Feature.class);
    private TextView appliedTagsTipTextView;
    private TextView clearAppliedFiltersTextView;
    private KeywordFilterDrawerController keywordFilterDrawerController;
    private ScrollView scrollView;

    public KeywordFilterDrawerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setKeywordFiltersPipelineListener(KeywordFiltersPipelineListener listener) {
        this.pipelineListener = listener;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_drawer_search_filters, this);
        scrollView = findView(R.id.view_drawer_search_filters_scrollview);
        appliedTagsTipTextView = findView(R.id.view_drawer_search_filters_touch_tag_tips);
        appliedTagsTipTextView.setVisibility(View.GONE);
        clearAppliedFiltersTextView = findView(R.id.view_drawer_search_filters_clear_all);
        clearAppliedFiltersTextView.setVisibility(View.GONE);

        findView(R.id.view_drawer_search_filters_exit_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onExitButtonClicked();
            }
        });

        clearAppliedFiltersTextView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                clearAppliedFilters();
            }
        });

        final EditText keywordEditText = findView(R.id.view_drawer_search_filters_keyword_edittext);
        keywordEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    actionId = EditorInfo.IME_ACTION_DONE;
                }
                return actionId == EditorInfo.IME_ACTION_DONE && onKeywordEntered(v);
            }
        });

        final ImageButton clearTextButton = findView(R.id.view_drawer_search_filters_keyword_text_button_clear);
        clearTextButton.setVisibility(RelativeLayout.GONE);
        clearTextButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                keywordEditText.setText("");
            }
        });

        keywordEditText.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearTextButton.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void afterTextChanged(Editable s) {
            }
        });

        featureContainer.put(KeywordDetector.Feature.SEARCH_SOURCE,
                new TagsController(
                        (TextView) findView(R.id.view_drawer_search_filters_search_sources_textview),
                        (ViewGroup) findView(R.id.view_drawer_search_filters_search_sources)));
        featureContainer.put(KeywordDetector.Feature.FILE_EXTENSION,
                new TagsController(
                        (TextView) findView(R.id.view_drawer_search_filters_file_extensions_textview),
                        (ViewGroup) findView(R.id.view_drawer_search_filters_file_extensions)));
        featureContainer.put(KeywordDetector.Feature.FILE_NAME,
                new TagsController(
                        (TextView) findView(R.id.view_drawer_search_filters_file_names_textview),
                        (ViewGroup) findView(R.id.view_drawer_search_filters_file_names)));
    }

    private void onExitButtonClicked() {
        if (keywordFilterDrawerController != null) {
            keywordFilterDrawerController.closeKeywordFilterDrawer();
        } else {
            LOG.warn("Check your logic, the keyword filter drawer controller has not been assigned");
        }
    }

    private void resetTagsContainers() {
        for (TagsController c : featureContainer.values()) {
            c.reset();
        }
    }

    private boolean onKeywordEntered(TextView v) {
        String keyword = v.getText().toString().trim().toLowerCase();
        if (keyword.length() == 0) {
            return true;
        }
        KeywordFilter keywordFilter = new KeywordFilter(true, keyword, (KeywordDetector.Feature) null);
        v.setText("");
        v.clearFocus();
        if (pipelineListener != null) {
            pipelineListener.onAddKeywordFilter(keywordFilter);
            updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
        }
        UIUtils.hideKeyboardFromActivity((Activity) getContext());
        return true;
    }

    private void clearAppliedFilters() {
        ViewGroup flowLayout = findView(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        updateAppliedKeywordFilters(Collections.<KeywordFilter>emptyList());
        for (TagsController c : featureContainer.values()) {
            c.restore();
        }
        scrollView.scrollTo(0, 0);
    }

    public void updateData(List<KeywordFilter> keywordFiltersPipeline, KeywordDetector.Feature feature, Entry<String, Integer>[] histogram) {
        if (keywordFiltersPipeline != null) {
            updateAppliedKeywordFilters(keywordFiltersPipeline);
        }
        TagsController tagsController = featureContainer.get(feature);
        if (feature != null && histogram != null && histogram.length > 0) {
            tagsController.showHeader();
            List<Entry<String, Integer>> filteredHistogram = highPassFilter(histogram, feature.filterThreshold);
            updateSuggestedKeywordFilters(feature, filteredHistogram);
        } else if (histogram.length == 0 && tagsController != null) {
            tagsController.hideHeader();
        }
        invalidate();
    }

    private List<Entry<String, Integer>> highPassFilter(Entry<String, Integer>[] histogram, float threshold) {
        int high = 0;
        int totalCount = 0;
        for (Entry<String, Integer> entry : histogram) {
            int count = entry.getValue();
            totalCount += count;
            if (count > high) {
                high = count;
            }
        }
        List<Entry<String, Integer>> filteredValues = new LinkedList<>();
        for (Entry<String, Integer> entry : histogram) {
            float rate = (float) entry.getValue() / (high + totalCount);
            if (entry.getValue() > 1 && rate >= threshold) {
                filteredValues.add(entry);
                //LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is IN");
            } //else {
            //LOG.info("<<< highPassFilter(total= " + totalCount + ", high=" + high + ", high+total=" + (high + totalCount) + ", rate=" + rate + "): <" + entry.getKey() + ":" + entry.getValue() + "> is OUT");
            //}
        }
        // sort'em!
        // TODO: review this sort in a LinkedList, bad bad performance
        Collections.sort(filteredValues, new Comparator<Entry<String, Integer>>() {
            @Override
            public int compare(Entry<String, Integer> o1, Entry<String, Integer> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });
        return filteredValues;
    }

    private void updateSuggestedKeywordFilters(KeywordDetector.Feature feature, List<Entry<String, Integer>> histogram) {
        ViewGroup container = featureContainer.get(feature).container;
        container.removeAllViews();
        boolean keywordsApplied = false;
        List<KeywordFilter> keywordFiltersPipeline = null;
        if (pipelineListener != null) {
            keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
            keywordsApplied = keywordFiltersPipeline.size() > 0;
        }
        for (Entry<String, Integer> entry : histogram) {
            int visibility = (keywordsApplied && keywordInPipeline(entry.getKey(), keywordFiltersPipeline)) ? View.GONE : View.VISIBLE;
            KeywordTagView keywordTagView = new KeywordTagView(getContext(), new KeywordFilter(true, entry.getKey(), feature), entry.getValue(), false, this);
            container.addView(keywordTagView);
            keywordTagView.setVisibility(visibility);
        }
        container.invalidate();
    }

    private boolean keywordInPipeline(String keyword, List<KeywordFilter> pipeline) {
        for (KeywordFilter filter : pipeline) {
            if (filter.getKeyword().equals(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void updateAppliedKeywordFilters(List<KeywordFilter> keywordFiltersPipeline) {
        boolean filtersHaveBeenApplied = keywordFiltersPipeline.size() > 0;
        int textViewsVisibility = filtersHaveBeenApplied ? View.VISIBLE : View.GONE;
        clearAppliedFiltersTextView.setVisibility(textViewsVisibility);
        appliedTagsTipTextView.setVisibility(textViewsVisibility);

        ViewGroup flowLayout = findView(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeAllViews();
        if (filtersHaveBeenApplied) {
            for (KeywordFilter filter : keywordFiltersPipeline) {
                KeywordTagView keywordTagView = new KeywordTagView(getContext(), filter, -1, true, this);
                flowLayout.addView(keywordTagView);
            }
        }
        if (pipelineListener != null) {
            pipelineListener.onPipelineUpdate(keywordFiltersPipeline);
        }
    }

    /**
     * KeywordTagViewListener.onDismissed
     */
    @Override
    public void onKeywordTagViewDismissed(KeywordTagView view) {
        ViewGroup flowLayout = findView(R.id.view_drawer_search_filters_pipeline_layout);
        flowLayout.removeView(view);

        if (pipelineListener != null) {
            // this will update the keywordFiltersPipeline
            pipelineListener.onRemoveKeywordFilter(view.getKeywordFilter());
            updateAppliedKeywordFilters(pipelineListener.getKeywordFiltersPipeline());
        }

        // unhide tag in container
        if (view.getKeywordFilter().getFeature() != null) {
            ViewGroup container = featureContainer.get(view.getKeywordFilter().getFeature()).container;
            for (int i = 0; i < container.getChildCount(); i++) {
                KeywordTagView keywordTagView = (KeywordTagView) container.getChildAt(i);
                if (keywordTagView.getKeywordFilter().getKeyword().equals(view.getKeywordFilter().getKeyword())) {
                    keywordTagView.setVisibility(View.VISIBLE);
                    break;
                }
            }
        }
    }

    @Override
    public void onKeywordTagViewTouched(KeywordTagView view) {
        if (pipelineListener == null) {
            return;
        }
        // if it's a dismissible one it's one of the applied filters
        List<KeywordFilter> keywordFiltersPipeline = pipelineListener.getKeywordFiltersPipeline();
        KeywordFilter keywordFilter = view.getKeywordFilter();
        if (view.isDismissible()) {
            int oldIndex = keywordFiltersPipeline.indexOf(keywordFilter);
            keywordFilter = view.toogleFilterInclusionMode();
            keywordFiltersPipeline.add(oldIndex, keywordFilter);
            keywordFiltersPipeline.remove(oldIndex + 1);
        } else {
            // attempt to add to pipeline
            if (!keywordFiltersPipeline.contains(keywordFilter)) {
                pipelineListener.onAddKeywordFilter(keywordFilter);
            }
            view.setVisibility(View.GONE);
        }
        updateAppliedKeywordFilters(keywordFiltersPipeline);
        scrollView.scrollTo(0, 0);
    }

    public void reset() {
        // visual reset
        ((Activity) getContext()).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                clearAppliedFilters();
                resetTagsContainers();
            }
        });
    }

    public void setKeywordFilterDrawerController(KeywordFilterDrawerController keywordFilterDrawerController) {
        this.keywordFilterDrawerController = keywordFilterDrawerController;
    }

    @SuppressWarnings("unchecked")
    private <T extends View> T findView(int id) {
        return (T) super.findViewById(id);
    }

    public interface KeywordFilterDrawerController {
        void closeKeywordFilterDrawer();

        void openKeywordFilterDrawer();
    }

    public interface KeywordFiltersPipelineListener {
        void onPipelineUpdate(List<KeywordFilter> pipeline);

        void onAddKeywordFilter(KeywordFilter keywordFilter);

        void onRemoveKeywordFilter(KeywordFilter keywordFilter);

        List<KeywordFilter> getKeywordFiltersPipeline();
    }

    // this is a mini controller for a sub-view of
    // tags, consisting of the label-header and the container
    private static final class TagsController {

        final TextView header;
        final ViewGroup container;

        TagsController(TextView header, ViewGroup container) {
            this.header = header;
            this.container = container;

            this.header.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle();
                }
            });
        }

        boolean isExpanded() {
            return container.getVisibility() == View.VISIBLE;
        }

        void expand() {
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.filter_minimize, 0, 0, 0);
            container.setVisibility(View.VISIBLE);
        }

        void collapse() {
            header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.filter_expand, 0, 0, 0);
            container.setVisibility(View.GONE);
        }

        void toggle() {
            if (isExpanded()) {
                collapse();
            } else {
                expand();
            }
        }

        void hideHeader() {
            header.setVisibility(View.GONE);
        }

        void showHeader() {
            header.setVisibility(View.VISIBLE);
        }

        void restore() {
            int count = container.getChildCount();
            for (int i = 0; i < count; i++) {
                container.getChildAt(i).setVisibility(View.VISIBLE);
            }
            // in case it was "collapsed"
            expand();
        }

        void reset() {
            container.removeAllViews();
            expand();
        }
    }
}
