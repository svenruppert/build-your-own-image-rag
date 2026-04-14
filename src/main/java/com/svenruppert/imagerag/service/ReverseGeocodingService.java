package com.svenruppert.imagerag.service;

import com.svenruppert.imagerag.domain.LocationSummary;

public interface ReverseGeocodingService {

  LocationSummary reverseGeocode(double latitude, double longitude);
}
