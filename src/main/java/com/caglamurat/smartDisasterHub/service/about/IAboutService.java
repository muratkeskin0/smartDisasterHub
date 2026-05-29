package com.caglamurat.smartDisasterHub.service.about;

import com.caglamurat.smartDisasterHub.dto.about.AboutDTO;

public interface IAboutService {
    
    AboutDTO getAbout();
    
    AboutDTO updateAbout(AboutDTO aboutDTO);
    
    AboutDTO createAbout(AboutDTO aboutDTO);
}





