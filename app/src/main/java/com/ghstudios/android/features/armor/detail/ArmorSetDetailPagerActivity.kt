package com.ghstudios.android.features.armor.detail

import android.arch.lifecycle.ViewModelProviders
import com.ghstudios.android.BasePagerActivity
import com.ghstudios.android.MenuSection
import com.ghstudios.android.mhgendatabase.R

class ArmorSetDetailPagerActivity : BasePagerActivity() {
    companion object {
        const val EXTRA_ARMOR_ID = "com.daviancorp.android.android.ui.detail.armor_id"
        const val EXTRA_FAMILY_ID = "com.daviancorp.android.android.ui.detail.family_id"
    }

    private val viewModel by lazy {
        ViewModelProviders.of(this).get(ArmorSetDetailViewModel::class.java)
    }

    override fun getSelectedSection() = MenuSection.ARMOR

    override fun onAddTabs(tabs: TabAdder) {
        this.hideTabsIfSingular()

        val armorId = intent.getLongExtra(EXTRA_ARMOR_ID, -1)
        val familyId = intent.getLongExtra(EXTRA_FAMILY_ID, -1)

        val metadata = when (familyId > -1) {
            true -> viewModel.initByFamily(familyId)
            false -> viewModel.initByArmor(armorId)
        }

        title = metadata.first().familyName
        val hasSummaryTab = metadata.size > 1

        if (hasSummaryTab) {
            tabs.addTab(getString(R.string.summary)){ ArmorSetSummaryFragment() }
        }

        for ((idx, armorData) in metadata.withIndex()) {
            tabs.addTab(armorData.slot) {
                ArmorDetailFragment.newInstance(armorData.id)
            }

            // If this is the armor we came here for, set it as the default tab
            if (armorData.id == armorId) {
                val preTabCount = if (hasSummaryTab) 1 else 0
                tabs.setDefaultItem(idx + preTabCount)
            }
        }
    }
}