package com.bscs13b.offloadhpc.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * ViewPager2 adapter that returns the four job-submission fragments.
 * v2.0 — extended with Image Processing and K-Means tabs.
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
            case 2:
                return new ImageProcFragment();
            case 3:
                return new KMeansFragment();
            default:
                return new MatMulFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}

