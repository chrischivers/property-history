## Property History Application

This project is a work in progress.

The intention is to provide a cohesive, historical view of the listing history for a particular property, going back
many years.

Currently major property platforms (such as Rightmove) lack this functionality. A user can search for and view a
current listing, but not view previous listings for the same property.
They also are unable to see how the details for a particular listing have changed over time.

This information is valuable for a potential buyer as it surfaces more information about a particular property. It is
also potentially useful for researchers.

This project consists of three separate modules:

| Module       | Description                                                                                                                        |
|--------------|------------------------------------------------------------------------------------------------------------------------------------|
| Crawler      | Crawls the Rightmove website, scraping details for for new property listing, as well as updating the details of existing listings. |
| Backend API  | An API to expose details of properties to the frontend                                                                             |
| Frontend | A primitive UI to allow a user to search by entering a Rightmove url                                                               |