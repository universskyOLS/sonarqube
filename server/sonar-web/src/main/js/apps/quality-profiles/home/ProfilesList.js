/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import React from 'react';
import { Link, PropTypes as RouterPropTypes } from 'react-router';
import groupBy from 'lodash/groupBy';
import pick from 'lodash/pick';
import sortBy from 'lodash/sortBy';
import ProfilesListRow from './ProfilesListRow';
import ProfilesListHeader from './ProfilesListHeader';
import RestoreBuiltInProfilesView from '../views/RestoreBuiltInProfilesView';
import { ProfilesListType, LanguagesListType } from '../propTypes';
import { translate, translateWithParameters } from '../../../helpers/l10n';
import { TooltipsContainer } from '../../../components/mixins/tooltips-mixin';

export default class ProfilesList extends React.Component {
  static propTypes = {
    profiles: ProfilesListType,
    languages: LanguagesListType,
    location: RouterPropTypes.location
  };

  handleRestoreBuiltIn (languageKey, e) {
    e.preventDefault();
    const language = this.props.languages.find(l => l.key === languageKey);
    new RestoreBuiltInProfilesView({ language })
        .on('done', this.props.updateProfiles)
        .render();
  }

  renderProfiles (profiles) {
    return profiles.map(profile => (
        <ProfilesListRow key={profile.key} profile={profile}/>
    ));
  }

  renderHeader (languageKey, profilesCount) {
    const language = this.props.languages.find(l => l.key === languageKey);
    return (
        <thead>
          <tr>
            <th>
              <Link
                  to={{ pathname: '/', query: { language: language.key } }}
                  className="link-base-color">
                {language.name}
              </Link>
              {', '}
              {translateWithParameters(
                  'quality_profiles.x_profiles',
                  profilesCount
              )}
              {this.props.canAdmin && (
                  <button
                      className="huge-spacer-left js-restore-built-in"
                      data-language={languageKey}
                      onClick={this.handleRestoreBuiltIn.bind(this, languageKey)}>
                    {translate('quality_profiles.restore_built_in_profiles')}
                  </button>
              )}
            </th>
            <th className="text-right nowrap">
              {translate('quality_profiles.list.projects')}
            </th>
            <th className="text-right nowrap">
              {translate('quality_profiles.list.rules')}
            </th>
            <th className="text-right nowrap">
              {translate('quality_profiles.list.updated')}
            </th>
            <th className="text-right nowrap">
              {translate('quality_profiles.list.used')}
            </th>
          </tr>
        </thead>
    );
  }

  render () {
    const { profiles, languages } = this.props;
    const { language } = this.props.location.query;

    const profilesIndex = groupBy(profiles, profile => profile.language);
    const profilesToShow = language ?
        pick(profilesIndex, language) :
        profilesIndex;

    const languagesToShow = sortBy(Object.keys(profilesToShow));

    return (
        <div>
          <ProfilesListHeader
              languages={languages}
              currentFilter={language}/>

          {Object.keys(profilesToShow).length === 0 && (
              <div className="alert alert-warning">
                {translate('no_results')}
              </div>
          )}

          {languagesToShow.map(languageKey => (
              <table
                  key={languageKey}
                  data-language={languageKey}
                  className="data zebra zebra-hover quality-profiles-table">

                {this.renderHeader(
                    languageKey,
                    profilesToShow[languageKey].length)}

                <TooltipsContainer>
                  <tbody>
                    {this.renderProfiles(profilesToShow[languageKey])}
                  </tbody>
                </TooltipsContainer>

              </table>
          ))}

        </div>
    );
  }
}