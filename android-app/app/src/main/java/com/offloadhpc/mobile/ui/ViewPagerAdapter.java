package com.offloadhpc.mobile.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 adapter that returns the two job-submission fragments.
 */
public class ViewPagerAdapter extends FragmentStateAdapter {

    public ViewPagerAdapter(@NonNull FragmentActivity fa) {
        super(fa);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new MatMulFragment();
            case 1:
                return new HashCrackFragment();
            default:
                return new MatMulFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }
}
